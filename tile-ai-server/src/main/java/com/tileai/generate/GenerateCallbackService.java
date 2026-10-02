package com.tileai.generate;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tileai.common.error.BusinessException;
import com.tileai.common.error.ErrorCode;
import com.tileai.common.storage.ImageTransloader;
import com.tileai.generate.dto.GenerateCallbackRequest;
import com.tileai.generate.dto.GenerateCallbackRequest.ImageRef;
import com.tileai.generate.dto.GenerateTaskPayload;
import com.tileai.generate.dto.PartSelection;
import com.tileai.generate.entity.GenerationTask;
import com.tileai.generate.mapper.GenerationTaskMapper;
import com.tileai.plan.PlanAutoSaveService;
import com.tileai.plan.dto.PlanImageRef;
import com.tileai.plan.dto.PlanPartRef;

/**
 * 生成结果回传的处理(契约 {@code openapi-internal.yaml} 的
 * {@code POST /internal/generate/callback};架构 §6.2、§6.4、§10.6④)。
 *
 * <h2>三类事件</h2>
 * <ul>
 *   <li>{@code running} —— 开工 + 进度,只更新状态与进度;</li>
 *   <li>{@code success} —— 终局。抢到"这一次"的那一方<b>顺带把方案存下来</b>;</li>
 *   <li>{@code failed} —— 终局。只记原因,<b>不删任务</b> —— 补偿扫描重投后它要回到
 *       {@code running}(契约:失败可重试)。</li>
 * </ul>
 *
 * <h2>幂等靠什么</h2>
 *
 * <p>靠 SQL 的 {@code WHERE task_id = ? AND status <> 'success'},不靠"先查一下再写"
 * (理由见 {@link GenerationTaskMapper#claimSuccess}:并发下"查一下"看到的状态是过期的)。
 * 抢输了的回调<b>返回成功</b>—— 契约原文就是"重复回传返回成功但被忽略",
 * 回一个错会让 Python 以为回调没送达而重试,白白放大一次重复。
 *
 * <p><b>抢闸门必须发生在存方案之前</b>:反过来的话,抢输了才发现方案已经插进去了,
 * 那份方案是回不掉的(它在一个已经提交或即将提交的事务里)。所以顺序是
 * 先抢 → 再存 → 再回填 planId。
 *
 * <p><b>状态更新与方案保存必须同一事务</b>:要么都成、要么都不成。
 * 只有这一点保证了"任务说 success ⟺ 方案真的存在"。
 *
 * <p>但<b>转存图片不在事务里</b>(见 {@link #handle})—— 那是网络 + 磁盘 I/O,
 * 圈进事务会一直占着数据库连接,而且下载失败会连状态更新一起回滚。切分方式
 * 照 {@code GenerateService#accept} 的既有范式:{@code handle} 本身不在事务里,
 * 真正要原子的那一段由 {@link TransactionTemplate} 包住。
 */
@Service
public class GenerateCallbackService {

    private static final Logger log = LoggerFactory.getLogger(GenerateCallbackService.class);

    /** {@code t_generation_task.error} 的列宽,超出会被 MySQL 直接拒掉。 */
    private static final int ERROR_MAX = 1000;

    /** {@code t_generation_task.result_ref} 的列宽。 */
    private static final int RESULT_REF_MAX = 500;

    /**
     * 基准机位的视角名。
     *
     * <p>D-5(2026-09-30)定的约定:N 张图的视角是 {@code default} + {@code angle1}…
     * {@code angleN-1}。所以 {@code default} 唯一,用它当封面是确定的,
     * 不需要"再挑一张"。
     */
    private static final String VIEW_DEFAULT = "default";

    private final GenerationTaskMapper taskMapper;
    private final PlanAutoSaveService planAutoSaveService;
    private final ObjectMapper objectMapper;
    private final ImageTransloader imageTransloader;
    private final TransactionTemplate transactionTemplate;

    public GenerateCallbackService(GenerationTaskMapper taskMapper,
                                   PlanAutoSaveService planAutoSaveService,
                                   ObjectMapper objectMapper,
                                   ImageTransloader imageTransloader,
                                   TransactionTemplate transactionTemplate) {
        this.taskMapper = taskMapper;
        this.planAutoSaveService = planAutoSaveService;
        this.objectMapper = objectMapper;
        this.imageTransloader = imageTransloader;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * 处理一次回调。
     *
     * <p><b>本方法本身不在事务里。</b>成功回调要先做一步"把图抓回本地"的
     * 网络 + 磁盘 I/O,而这一步有三个理由必须待在事务外:
     * <ol>
     *   <li>圈进事务会一直占着数据库连接(下载最坏几秒);</li>
     *   <li>下载失败必须<b>降级</b>而不是让事务回滚 —— 回滚会把任务退回可重试,
     *       补偿扫描随后重投,Python 会<b>重跑一次真实模型</b>;</li>
     *   <li>真正要原子的是"状态 + 方案",而它与下载无关。</li>
     * </ol>
     *
     * <p>⚠️ <b>转存必须发生在 {@code claimSuccess} 之前。</b>那个闸门在 SQL 里是
     * 单向的({@code WHERE status <> 'success'}),<b>没有对应的"撤回"</b>。
     * 若先抢闸门、提交、再转存、再另起一个事务存方案,一旦存方案失败就得回退
     * 一个已经提交的 success —— 那会引入新的状态机分支。放在前面,则
     * 抢闸门 / 存方案 / 回填 planId 仍在<b>同一事务</b>里原子完成。
     *
     * <p>代价是重复的 success 回调会多下载一次。可以接受:Python 不重发回调
     * (见 {@code orchestrator._notify}),而补偿重投时任务本来就被重新生成过、
     * 图也确实该重抓;落盘是内容寻址的,重复写幂等,数据库侧仍被闸门挡住。
     *
     * @throws BusinessException 40003 —— taskId 不存在
     * @throws BusinessException 10001 —— {@code success} 却没给图片
     */
    public void handle(GenerateCallbackRequest request) {
        GenerateCallbackRequest effective = archiveImagesIfSuccess(request);
        transactionTemplate.executeWithoutResult(status -> dispatch(effective));
    }

    /** 真正要原子的那一段。由 {@link TransactionTemplate} 调用。 */
    private void dispatch(GenerateCallbackRequest request) {
        String taskId = request.taskId();
        switch (request.status()) {
            case GenerationTask.STATUS_RUNNING -> handleRunning(taskId, request);
            case GenerationTask.STATUS_SUCCESS -> handleSuccess(taskId, request);
            case GenerationTask.STATUS_FAILED -> handleFailed(taskId, request);
            // 契约的 TaskStatus 里有 pending,但受理时任务就已经是 pending 了,
            // 回调再报一次 pending 不改变任何东西。静默忽略太像"接收了" ——
            // 记一条 WARN,让"Python 在回调里发 pending"这件事在日志里看得见。
            case GenerationTask.STATUS_PENDING ->
                    log.warn("回调带了 pending 状态,不做状态变更(受理时已是 pending)taskId={}", taskId);
            default -> throw new BusinessException(ErrorCode.PARAM_INVALID,
                    "未知任务状态: " + request.status());
        }
    }

    /**
     * 成功回调的前置:把外部临时地址换成我们自己的地址(架构 §15.1)。
     *
     * <p>真实图像模型给的是 24 小时过期的 OSS 地址,直接入库等于"图明天就没了"。
     * 这里换成 {@code {publicBaseUrl}/media/…} 之后,库里存的是长期有效的地址
     * —— <b>顺带把 {@code t_generation_task.result_ref}(进度接口返回的封面)</b>
     * 也一并换掉了,因为那条路径读的正是同一份图片列表。
     *
     * <p>转存失败或未启用时<b>原样返回</b>,行为与没有这个功能时完全一致。
     */
    private GenerateCallbackRequest archiveImagesIfSuccess(GenerateCallbackRequest request) {
        if (!GenerationTask.STATUS_SUCCESS.equals(request.status())) {
            return request;
        }
        List<ImageRef> images = request.imageUrls();
        if (images == null || images.isEmpty()) {
            // 交给 handleSuccess 报 10001 —— 那是"契约被违反",不该在这里悄悄吞掉
            return request;
        }

        List<ImageRef> localized = imageTransloader.localize(images, request.taskId());
        if (localized == images) {
            return request;
        }
        return new GenerateCallbackRequest(
                request.taskId(),
                request.status(),
                localized,
                request.progress(),
                request.error(),
                request.traceId());
    }

    // ------------------------------------------------------------------

    /**
     * 进度回调。
     *
     * <p>{@code progress} 在 SQL 里取 {@code GREATEST} —— 多张图各自回调、顺序不定,
     * 不能让"第 1 张的 25%"盖掉"第 2 张的 50%"。进度条倒退比不动更让人怀疑系统坏了。
     */
    private void handleRunning(String taskId, GenerateCallbackRequest request) {
        int progress = request.progressOrDefault();
        if (taskMapper.markRunning(taskId, progress) > 0) {
            return;
        }
        // 0 行只有两种可能:任务不存在,或者已经 success(终态)。前者是接口用错了,
        // 后者是迟到的进度回调 —— 两种情况要给出完全不同的回应。
        requireTask(taskId);
        log.debug("任务已是 success,忽略迟到的进度回调 taskId={} progress={}", taskId, progress);
    }

    /**
     * 失败回调。
     *
     * <p>{@code failed} 不是终态:补偿扫描重投后任务会回到 {@code running}。
     * 这里也不清 {@code plan_id} —— 出图失败不该顺手把用户原本的方案改掉。
     */
    private void handleFailed(String taskId, GenerateCallbackRequest request) {
        int progress = request.progressOrDefault();
        String error = errorTextOf(request);

        if (taskMapper.markFailed(taskId, progress, error) > 0) {
            log.info("生成任务失败 taskId={} progress={} error={}", taskId, progress, error);
            return;
        }
        requireTask(taskId);
        log.debug("任务已是 success,忽略迟到的失败回调 taskId={} error={}", taskId, error);
    }

    /**
     * 成功回调:抢闸门 → 存方案 → 回填 planId。三步同一事务。
     */
    private void handleSuccess(String taskId, GenerateCallbackRequest request) {
        List<PlanImageRef> images = toPlanImages(request.imageUrls());
        if (images.isEmpty()) {
            // 契约:status=success 时 imageUrls 必填。真发生了说明 Python 侧有 bug,
            // 报出去比"存一个没有图的方案"好 —— 后者用户点进去是一张空白卡片。
            throw new BusinessException(ErrorCode.PARAM_INVALID,
                    "status=success 时 imageUrls 不能为空");
        }

        // ① 抢闸门。抢不到就什么都不做 —— 这是"重复回调不产生副作用"的唯一实现点。
        if (taskMapper.claimSuccess(taskId, coverUrlOf(images)) == 0) {
            requireTask(taskId);
            log.info("任务已是 success,重复回调被忽略(未改动任何数据)taskId={}", taskId);
            return;
        }

        // ② 存方案。此时状态已是 success,但这还在事务里 —— 存方案失败会连它一起回滚,
        //    任务退回可重试,而不是留下"任务说成功、方案却没有"。
        GenerationTask task = requireTask(taskId);
        GenerateTaskPayload payload = payloadOf(task);
        long planId = planAutoSaveService.upsert(
                task.getUserId(),
                task.getPlanId(),
                payload.space(),
                payload.style(),
                payload.layout(),
                toPlanParts(payload.parts()),
                images);

        // ③ 回填。新建方案时受理阶段还不知道这个 id,前端靠它跳转到方案详情。
        taskMapper.bindPlan(taskId, planId);

        log.info("生成任务完成 taskId={} planId={} userId={} images={}",
                taskId, planId, task.getUserId(), images.size());
    }

    // ------------------------------------------------------------------

    /**
     * 取任务行,取不到就 40003。
     *
     * <p>只在已经确定"这一行本该存在"的地方调用(抢到闸门之后、或闸门返回 0 行需要
     * 分辨原因时)—— 它是一次额外的查询,不该出现在正常路径的主干上。
     */
    private GenerationTask requireTask(String taskId) {
        GenerationTask task = taskMapper.selectOne(Wrappers.<GenerationTask>lambdaQuery()
                .eq(GenerationTask::getTaskId, taskId));
        if (task == null) {
            throw new BusinessException(ErrorCode.TASK_NOT_FOUND, "任务不存在: " + taskId);
        }
        return task;
    }

    /**
     * 回调的图片列表 → 落库用的引用。
     *
     * <p>封面口径:{@code isCover} 给了就听它的,没给就按视角判 —— {@code default}
     * 是基准机位(D-5),它当封面。两者都不成立时退而取第一张:
     * 方案列表要渲染一张卡片,没有封面会渲染成空图。
     *
     * <p><b>封面只允许一张</b>:回调若标了两个(或一个都没标而第一张又不是 default),
     * 以先出现的为准并记 WARN。多张封面不是"更丰富",而是让"哪张当卡片"
     * 取决于数据库返回顺序 —— 那是随机行为,该被摁掉。
     */
    private List<PlanImageRef> toPlanImages(List<GenerateCallbackRequest.ImageRef> images) {
        if (images == null || images.isEmpty()) {
            return List.of();
        }
        List<PlanImageRef> refs = new ArrayList<>(images.size());
        int coverIndex = -1;
        for (int i = 0; i < images.size(); i++) {
            GenerateCallbackRequest.ImageRef image = images.get(i);
            boolean cover = image.isCover() != null
                    ? image.isCover()
                    : VIEW_DEFAULT.equals(image.view());
            if (cover && coverIndex >= 0) {
                log.warn("回调里有多个封面视角,只保留第一个 url={} 其余降级为普通图",
                        images.get(coverIndex).url());
                cover = false;
            }
            if (cover) {
                coverIndex = i;
            }
            refs.add(new PlanImageRef(image.url(), image.view(), cover));
        }
        if (coverIndex < 0) {
            log.warn("回调里没有标记封面的视角,把第一张当封面 url={}", images.get(0).url());
            refs.set(0, new PlanImageRef(images.get(0).url(), images.get(0).view(), true));
        }
        return refs;
    }

    /** 封面 url,写进 {@code result_ref}。{@link #toPlanImages} 已保证恰好一张。 */
    private static String coverUrlOf(List<PlanImageRef> images) {
        String url = images.stream()
                .filter(PlanImageRef::isCover)
                .map(PlanImageRef::url)
                .findFirst()
                .orElse(images.get(0).url());
        return url.length() > RESULT_REF_MAX ? url.substring(0, RESULT_REF_MAX) : url;
    }

    /**
     * 部位列表 → 方案部位。
     *
     * <p><b>{@code source} 是保留的,不是丢掉的</b> —— 这条注释原先写的是
     * 「去掉 source:那是生成口径,不是方案口径」,那个判断被 T-2.1 的契约↔实现
     * 比对推翻了:契约 {@code PlanPart.source} 是**方案**详情要返回的字段,
     * 而 PRD §5.2.4 明确「补齐结果随方案自动保存」。丢了它,详情接口就永远说不出
     * 哪块砖是 AI 补的。
     *
     * <p>归一用 {@link PartSelection#withDefaultSource()}(契约:请求里可省略,
     * 默认 {@code user})—— 不在这里写第二次判空。
     */
    private static List<PlanPartRef> toPlanParts(List<PartSelection> parts) {
        if (parts == null) {
            return List.of();
        }
        return parts.stream()
                .map(PartSelection::withDefaultSource)
                .map(part -> new PlanPartRef(part.part(), part.sku(), part.area(), part.source()))
                .toList();
    }

    private GenerateTaskPayload payloadOf(GenerationTask task) {
        try {
            return objectMapper.readValue(task.getPayload(), GenerateTaskPayload.class);
        } catch (JsonProcessingException e) {
            // 快照坏了就存不成方案。必须抛出去让整个事务回滚(连状态更新一起),
            // 否则会留下一个 success 但没有任何方案、没有图的任务 —— 前端会一直
            // 轮询到一个空结果,却看不出哪里错了。抛出去 Python 会重试,
            // 补偿扫描最终把它判成 failed,用户至少能看到一次明确的失败。
            throw new IllegalStateException("任务快照无法解析,无法保存方案: " + task.getTaskId(), e);
        }
    }

    /** 失败原因,截到列宽。空则落一句默认文案 —— {@code error} 为空串等于没写原因。 */
    private static String errorTextOf(GenerateCallbackRequest request) {
        String message = request.error() == null ? null : request.error().message();
        if (message == null || message.isBlank()) {
            return ErrorCode.GENERATE_FAILED.defaultMessage();
        }
        return message.length() > ERROR_MAX ? message.substring(0, ERROR_MAX) : message;
    }
}
