package com.tileai.generate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tileai.common.error.BusinessException;
import com.tileai.common.error.ErrorCode;
import com.tileai.common.mq.RabbitTopologyConfig;
import com.tileai.common.outbox.OutboxMessage;
import com.tileai.common.outbox.OutboxService;
import com.tileai.common.trace.TraceContext;
import com.tileai.generate.dto.GenerateAccepted;
import com.tileai.generate.dto.GenerateRequest;
import com.tileai.generate.dto.GenerateTaskPayload;
import com.tileai.generate.dto.PartSelection;
import com.tileai.generate.dto.TaskProgressView;
import com.tileai.generate.entity.GenerationTask;
import com.tileai.generate.mapper.GenerationTaskMapper;
import com.tileai.layout.LayoutAreaCalculator;
import com.tileai.layout.model.LayoutModel;
import com.tileai.plan.PlanAutoSaveService;
import com.tileai.plan.entity.PlanImage;
import com.tileai.plan.mapper.PlanImageMapper;

/**
 * 生成任务的受理与查询(契约 {@code openapi-c.yaml} 的 generate 分组)。
 *
 * <pre>
 *   POST /api/v1/generate           受理,落任务 + 落发件箱(同一事务)
 *   GET  /api/v1/generate/{taskId}  轮询进度 / 结果
 * </pre>
 *
 * <h2>为什么受理要写成"落库 + 发件箱",而不是"落库 + 直接发 MQ"</h2>
 *
 * 直接发 MQ 的话,"任务行写进去了、消息没发出去"这一瞬间会留下一个永远 {@code pending}
 * 的孤儿任务 —— 而且它看起来完全正常,只是永远不出图。发件箱把它变成一个问题:
 * 要么两个都成,要么两个都不成,消息交给独立投递器去发(架构 §6.4)。
 *
 * <p>所以本类的 {@code accept} 里,"落任务 + 落发件箱"那一段在事务内,而
 * {@link OutboxService#enqueue} 刻意<b>不带</b> {@code @Transactional} —— 它要加入
 * 调用方这个事务。这是那两处注解必须一起看的唯一原因。
 *
 * <h2>为什么事务不是整个方法(T-1.9)</h2>
 *
 * <p>受理里多了一步"问 Python 补齐未选部位"(架构 §11.2:在落任务之前补),
 * 而那是一次**可能几十秒**的网络调用。若整个方法仍是 {@code @Transactional},
 * 这次调用会**一直占着数据库连接** —— 并发一上来连接池就空了。
 * 所以改成:补齐在事务外跑,只有"插任务 + 插发件箱"进事务
 * ({@link TransactionTemplate})。
 */
@Service
public class GenerateService {

    private static final Logger log = LoggerFactory.getLogger(GenerateService.class);

    /** 对外任务号前缀,形状对齐契约示例 {@code g_1027f3}。 */
    private static final String TASK_ID_PREFIX = "g_";

    /**
     * 时间口径与 {@code IndexSyncService} 一致({@code ISO_LOCAL_DATE_TIME})。
     *
     * <p>契约声明的是 {@code format: date-time},严格讲应带时区偏移。但同一套契约里
     * 索引同步消息用的就是这个格式、Python 侧也已经这么解析了 —— 这里跟着走,
     * 不为了"更严格"制造第二种时间格式。要统一改就该两边一起改。
     */
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    /** 失败文案回给前端的长度上限。数据库里存的是全文,这里只截显示用的那份。 */
    private static final int ERROR_DISPLAY_MAX = 200;

    private final GenerationTaskMapper taskMapper;
    private final PlanImageMapper planImageMapper;
    private final OutboxService outboxService;
    private final PlanAutoSaveService planAutoSaveService;
    private final PartCompletionService partCompletionService;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final GenerateProperties generateProperties;
    private final GenerationConsumerProbe consumerProbe;

    public GenerateService(GenerationTaskMapper taskMapper, PlanImageMapper planImageMapper,
                           OutboxService outboxService, PlanAutoSaveService planAutoSaveService,
                           PartCompletionService partCompletionService,
                           ObjectMapper objectMapper, TransactionTemplate transactionTemplate,
                           GenerateProperties generateProperties, GenerationConsumerProbe consumerProbe) {
        this.taskMapper = taskMapper;
        this.planImageMapper = planImageMapper;
        this.outboxService = outboxService;
        this.planAutoSaveService = planAutoSaveService;
        this.partCompletionService = partCompletionService;
        this.objectMapper = objectMapper;
        this.transactionTemplate = transactionTemplate;
        this.generateProperties = generateProperties;
        this.consumerProbe = consumerProbe;
    }

    // ------------------------------------------------------------------
    // 受理
    // ------------------------------------------------------------------

    /**
     * 受理一次生成。
     *
     * <p><b>本方法本身不在事务里</b> —— 中间那步"问 Python 补齐未选部位"是一次网络调用
     * (可能几十秒),把它圈进事务会一直占着数据库连接。真正要原子的那一段
     * ("落任务 + 落发件箱")在 {@link #persist} 里,由 {@link TransactionTemplate} 包住。
     *
     * <p><b>最前面先探一次"生成队列有没有消费端"</b>(见 {@link GenerationConsumerProbe})。
     * 没有就当场 40002 拒绝,而不是受理之后让用户盯一个进度永远为 0 的转圈看几分钟。
     * 探测在这一步最便宜 —— 排在归属校验与出网调用之前,不会白做任何工作。
     *
     * @return {@code (taskId, "pending")} —— 契约 {@code GenerateAccepted}
     * @throws BusinessException 40002 —— 生成队列上没有消费端(服务未启动/正在重启)
     */
    public GenerateAccepted accept(long userId, GenerateRequest request) {
        if (generateProperties.isRequireConsumer()) {
            consumerProbe.requireConsumer();
        }

        String taskId = TASK_ID_PREFIX + UUID.randomUUID().toString().replace("-", "");

        // 带 planId 就是"覆盖原方案"(架构 §11.4)。归属必须**现在**校验,不能拖到
        // 回调存方案时 —— 那要烧掉一次真实模型调用才失败(见 assertOverwritable)。
        planAutoSaveService.assertOverwritable(userId, request.planId());

        // 面积派生全项目只在服务端做一次(架构 §8.3)。在这里就补好再进 payload,
        // 好处是 Python 收到的户型里带面积、方案快照存的也是同一份 —— 而不是
        // 两边各推一遍、日后推出不一样的结果。
        LayoutModel layout = LayoutAreaCalculator.withDerivedAreas(request.layout());

        // source 归一成 user。契约里响应必填、请求可省,归一放在这一个地方,
        // 否则每个读取点都要判一次 null,必然有一处会漏。
        List<PartSelection> userParts = request.parts().stream()
                .map(PartSelection::withDefaultSource)
                .toList();

        // 未选的地面/墙面由 AI 补齐(架构 §11.2:在落任务之前补)。
        // **这一步在事务之外**;补不上会降级成原样返回(不是错误)。
        List<PartSelection> parts = partCompletionService.completeIfNeeded(
                request.space(), request.styleOrDefault(), userParts);

        return transactionTemplate.execute(status -> persist(taskId, userId, request, layout, parts));
    }

    /**
     * 把任务行与发件箱行**一起**写下去 —— 要么都成,要么都不成(架构 §6.4)。
     *
     * <p>由 {@link TransactionTemplate} 调用,所以这里**不需要** {@code @Transactional}
     * (自己调自己,注解根本不会生效 —— 那是个安静失效的陷阱)。
     */
    private GenerateAccepted persist(String taskId, long userId, GenerateRequest request,
                                     LayoutModel layout, List<PartSelection> parts) {
        // 序列化只做一次:同一串既写 t_generation_task.payload 又写 t_outbox.payload,
        // 投递器原样发出。所以"库里存的"与"Python 收到的"天然是同一份。
        String payload = toJson(new GenerateTaskPayload(
                taskId,
                GenerationTask.TYPE_IMAGE,
                TraceContext.getOrCreate(),
                userId,
                request.planId(),
                request.space(),
                request.styleOrDefault(),
                request.anglesOrDefault(),
                parts,
                layout,
                GenerateTaskPayload.INITIAL_ATTEMPT,
                LocalDateTime.now().format(ISO)));

        GenerationTask task = new GenerationTask();
        task.setTaskId(taskId);
        task.setUserId(userId);
        task.setPlanId(request.planId());
        task.setType(GenerationTask.TYPE_IMAGE);
        task.setStatus(GenerationTask.STATUS_PENDING);
        task.setProgress(0);
        task.setPayload(payload);
        taskMapper.insert(task);

        // 必须在同一事务内 —— 这正是 OutboxService 不自己开事务的原因。
        outboxService.enqueue(OutboxMessage.AGGREGATE_TASK, taskId,
                RabbitTopologyConfig.QUEUE_GENERATE, payload);

        log.info("受理生成任务 taskId={} userId={} planId={} parts={} angles={}",
                taskId, userId, request.planId(), parts.size(), request.anglesOrDefault());
        return new GenerateAccepted(taskId, GenerationTask.STATUS_PENDING);
    }

    // ------------------------------------------------------------------
    // 进度查询
    // ------------------------------------------------------------------

    /**
     * 查任务进度 / 结果。
     *
     * <p><b>按 {@code userId} 一起查</b>,不是查完再比对:"不是本人的任务"与"任务不存在"
     * 都必须是同一个 40003。区分开来就等于提供一个能枚举别人 taskId 的接口。
     *
     * @throws BusinessException 40003 —— 任务不存在或不属于当前用户
     */
    @Transactional(readOnly = true)
    public ProgressResult progress(long userId, String taskId) {
        GenerationTask task = taskMapper.selectOne(Wrappers.<GenerationTask>lambdaQuery()
                .eq(GenerationTask::getTaskId, taskId)
                .eq(GenerationTask::getUserId, userId));
        if (task == null) {
            throw new BusinessException(ErrorCode.TASK_NOT_FOUND, "任务不存在: " + taskId);
        }
        return new ProgressResult(toView(task), failureMessage(task));
    }

    /**
     * 失败时给前端看的那句话;非失败态为 null。
     *
     * <p>直接用回调带来的 {@code error.message}。它是<b>我们自己的 Python 编排器</b>
     * 写的展示文案(契约就是这么定的:"status=failed 时的错误信息"),不是第三方
     * SDK 的原始异常 —— 内部细节走 {@code traceId} 与日志。过长的截断,免得
     * 一屏塞满栈信息反而看不出结论。空串按"没说"处理,退回业务码的默认文案。
     */
    private static String failureMessage(GenerationTask task) {
        if (!GenerationTask.STATUS_FAILED.equals(task.getStatus())) {
            return null;
        }
        String error = task.getError();
        if (error == null || error.isBlank()) {
            return ErrorCode.GENERATE_FAILED.defaultMessage();
        }
        return error.length() > ERROR_DISPLAY_MAX
                ? error.substring(0, ERROR_DISPLAY_MAX) + "…"
                : error;
    }

    private TaskProgressView toView(GenerationTask task) {
        boolean success = GenerationTask.STATUS_SUCCESS.equals(task.getStatus());
        return new TaskProgressView(
                task.getTaskId(),
                task.getStatus(),
                task.getProgress(),
                task.getPlanId(),
                success ? imagesOf(task.getPlanId()) : null,
                partsOf(task.getPayload()));
    }

    /**
     * 方案里的效果图,按插入顺序返回。
     *
     * <p>{@code t_plan_image} 没有排序列,按自增 id 排 —— 写入顺序就是视角顺序
     * ({@code PlanAutoSaveService.replaceImages} 先删后插,插入序即期望展示序)。
     */
    private List<TaskProgressView.PlanImageView> imagesOf(Long planId) {
        if (planId == null) {
            return List.of();
        }
        return planImageMapper.selectList(Wrappers.<PlanImage>lambdaQuery()
                        .eq(PlanImage::getPlanId, planId)
                        .orderByAsc(PlanImage::getId))
                .stream()
                .map(row -> new TaskProgressView.PlanImageView(
                        row.getUrl(), row.getView(), row.getIsCover()))
                .toList();
    }

    /**
     * 从 payload 快照里取回部位列表。
     *
     * <p>解不开就返回空列表而<b>不抛异常</b>:这只是一个附加展示字段,让它把整次
     * 进度查询打成 90001,会连"任务已经成功"这个主结论一起吞掉。日志留痕即可。
     */
    private List<PartSelection> partsOf(String payload) {
        if (payload == null || payload.isBlank()) {
            return List.of();
        }
        try {
            GenerateTaskPayload parsed = objectMapper.readValue(payload, GenerateTaskPayload.class);
            return parsed.parts() == null ? List.of() : parsed.parts();
        } catch (JsonProcessingException e) {
            log.warn("任务 payload 无法解析,进度响应里省略 parts: {}", e.getOriginalMessage());
            return List.of();
        }
    }

    private String toJson(GenerateTaskPayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            // 序列化失败属编码问题,不能被静默吞掉 —— 抛出去让事务回滚,
            // 否则会留下一个 payload 为空的 pending 任务,而用户拿到 200 和 taskId。
            throw new IllegalStateException("生成任务消息体无法序列化", e);
        }
    }

    /**
     * 进度查询的结果:契约视图 + 失败文案。
     *
     * <p>分成两个字段而不是把 {@code error} 塞进 {@link TaskProgressView}:契约的
     * {@code TaskProgress} 里<b>没有</b> error 字段,失败原因的位置是
     * {@code ApiResponse.message}。多塞一个字段就是一处没人追得到的契约分叉。
     *
     * @param view           契约 {@code TaskProgress}(恒非空)
     * @param failureMessage 仅 {@code status=failed} 时非空,其余为 null
     */
    public record ProgressResult(TaskProgressView view, String failureMessage) {
    }
}
