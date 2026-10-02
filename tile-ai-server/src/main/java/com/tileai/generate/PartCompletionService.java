package com.tileai.generate;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tileai.common.agent.AgentClient;
import com.tileai.generate.dto.CompletePartsRequest;
import com.tileai.generate.dto.PartSelection;

/**
 * 未选部位的 AI 补齐(T-1.9;PRD §5.2.4、架构 §11.2)。
 *
 * <p>用户**不必选全**部位:没选的地面/墙面,在**受理生成时**由这里问一次 Python,
 * 把补齐结果并进部位列表。架构 §11.2 的时序就是"**落任务、投队列之前**先补齐" ——
 * 所以补齐发生在这里,而**不是**异步的生成消费端里。
 *
 * <p><b>三条口径:</b>
 * <ol>
 *   <li><b>只补 floor / wall</b> —— {@code local}(腰线/花砖)是可选部位,永不补齐;</li>
 *   <li><b>用户选的永远优先</b> —— 补齐只填用户没选的那几个部位,不覆盖任何已有选择;</li>
 *   <li><b>补不上就降级,不阻断生成</b>(2026-10-02 拍板)。用户不该因为"库里没有合适的砖"
 *       而被卡住 —— 拿不到补齐结果就<b>原样用用户的部位</b>继续跑,只记一条 WARN。
 *       代价是**失败静默**(只有日志),这一点已登记在
 *       {@code openapi-verification.md} §49。</li>
 * </ol>
 *
 * <p><b>不调 AI 的情形</b>:用户把地面和墙面都选了 —— 那连这一次网络请求都不发
 * (既省一次模型调用,也免得白占一次 30s 的超时窗口)。
 */
@Service
public class PartCompletionService {

    private static final Logger log = LoggerFactory.getLogger(PartCompletionService.class);

    /** 契约里的内部端点路径(不带 {@code /api/v1},见不变量 3)。 */
    public static final String COMPLETE_PATH = "/internal/agent/complete-parts";

    /**
     * 会被补齐的部位,**顺序即结果顺序**。
     *
     * <p>{@code local} **不在这里,也不该被加进来** —— 腰线/花砖是可选部位(PRD §5.2.4)。
     * 这个集合就是那条产品规则的落点。
     */
    static final List<String> COMPLETABLE = List.of(PartSelection.PART_FLOOR, PartSelection.PART_WALL);

    private final AgentClient agentClient;
    private final ObjectMapper objectMapper;

    public PartCompletionService(AgentClient agentClient, ObjectMapper objectMapper) {
        this.agentClient = agentClient;
        this.objectMapper = objectMapper;
    }

    /**
     * 有缺就补齐,没有就把原样返回。
     *
     * <p><b>可能发起一次出网调用</b>(最坏几十秒),所以调用方**必须把它放在事务之外** ——
     * 见 {@code GenerateService.accept}。事务里做网络调用会一直占着数据库连接。
     *
     * @return 合并后的部位列表;**永不抛异常**(失败即降级,返回入参)
     */
    public List<PartSelection> completeIfNeeded(String space, String style,
                                                List<PartSelection> userParts) {
        List<String> missing = missingOf(userParts);
        if (missing.isEmpty()) {
            return userParts;
        }

        try {
            JsonNode data = agentClient.post(COMPLETE_PATH,
                    new CompletePartsRequest(space, style, userParts));
            return merge(userParts, parse(data));
        } catch (Exception error) {
            // 降级不阻断:补齐是**增强**,不是主流程。拿不到就照用户的部位跑。
            log.warn("部位补齐失败,已降级(本次不补齐) space={} missing={} 原因={}",
                    space, missing, error.toString());
            return userParts;
        }
    }

    /** 缺哪些部位。判"缺"是看**有没有这个 part**,不是数量 —— 同部位可以有多块砖。 */
    static List<String> missingOf(List<PartSelection> parts) {
        Set<String> chosen = new LinkedHashSet<>();
        for (PartSelection part : parts) {
            chosen.add(part.part());
        }
        return COMPLETABLE.stream().filter(part -> !chosen.contains(part)).toList();
    }

    private List<PartSelection> parse(JsonNode data) {
        JsonNode parts = data == null ? null : data.path("parts");
        if (parts == null || !parts.isArray()) {
            throw new IllegalStateException("补齐响应里没有 parts 数组");
        }
        return objectMapper.convertValue(parts, new TypeReference<List<PartSelection>>() { });
    }

    /**
     * 合并用户的部位与 Python 补齐的部位。**纯函数**(单独单测)。
     *
     * <p>规则逐条对应一条不变量:
     * <ol>
     *   <li><b>用户项一律保留并标 {@code user}</b> —— 不采信 Python 回显的用户项
     *       (它可能有出入),只取它**补**的那几项;</li>
     *   <li>补齐项标 {@code ai},且**只接受 floor / wall**;</li>
     *   <li><b>同部位冲突:用户赢</b> —— Python 若回了用户已选部位,丢弃;</li>
     *   <li>{@code local}、未知部位、缺 sku 的项一律丢弃(WARN)—— 防 Python 越界补 local;</li>
     *   <li>补齐项的 {@code area} 一律置空,由服务端依户型派生(全项目只在 Java 派生一次)。</li>
     * </ol>
     */
    static List<PartSelection> merge(List<PartSelection> userParts, List<PartSelection> returned) {
        Set<String> chosen = new LinkedHashSet<>();
        List<PartSelection> merged = new ArrayList<>();
        for (PartSelection part : userParts) {
            chosen.add(part.part());
            merged.add(new PartSelection(part.part(), part.sku(), part.area(), PartSelection.SOURCE_USER));
        }

        for (PartSelection item : returned) {
            String part = item.part();
            if (!COMPLETABLE.contains(part)) {
                log.warn("补齐结果里出现了不该补的部位,已丢弃 part={}", part);
                continue;
            }
            if (chosen.contains(part)) {
                // 用户已经选了这个部位 —— 用户优先,AI 不得覆盖
                continue;
            }
            if (item.sku() == null || item.sku().isBlank()) {
                log.warn("补齐结果里的部位没有 sku,已丢弃 part={}", part);
                continue;
            }
            merged.add(new PartSelection(part, item.sku(), null, PartSelection.SOURCE_AI));
            chosen.add(part); // 防 Python 对同一部位回显两条
        }
        return merged;
    }
}
