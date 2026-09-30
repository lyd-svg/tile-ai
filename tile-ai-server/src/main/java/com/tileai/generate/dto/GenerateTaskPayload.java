package com.tileai.generate.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.tileai.layout.model.LayoutModel;

/**
 * 生成任务消息体(异步契约
 * {@code asyncapi-internal.yaml#/components/schemas/GenerateTaskPayload})。
 *
 * <p>Java 产出、Python 消费,经 {@code queue.generate} 投递。字段名是 <b>camelCase</b>
 * —— 这是契约怎么写就怎么来,与 {@code IndexSyncPayload} 的 snake_case 不同,
 * 两处不要互相"统一"。
 *
 * <p><b>落库与投递是同一份字符串</b>:本对象序列化一次,同一串既写进
 * {@code t_generation_task.payload} 又写进 {@code t_outbox.payload},投递器原样发出。
 * 所以"库里存的"与"Python 收到的"天然一致,中间没有第二个构造点。
 *
 * <p><b>{@code parts} 是补齐后的完整部位列表</b>(契约注明)—— 一期尚未实现
 * 未选部位 AI 补齐(T-1.9),所以这里就是请求里的部位原样,{@code source} 恒为
 * {@code user}。等 T-1.9 落地后,补齐项会在这里标成 {@code source=ai}。
 *
 * <p>{@code @JsonInclude(NON_NULL)}:可选字段(如 {@code planId}、{@code userId})
 * 为 null 时整个省略。消息体是给 Python 看的,把 "planId": null 发过去只会让它多写
 * 一条判空分支 —— 而且契约里这些字段没标 {@code nullable}。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GenerateTaskPayload(
        /** 对外唯一任务号,幂等键。Python 消费与 Java 回调都以此去重。 */
        String taskId,
        String type,
        /** 全链路追踪 ID,跨进程定位用(架构 §13.7)。 */
        String traceId,
        Long userId,
        /** 非空表示覆盖原方案(架构 §11.4 覆盖分支);另存为走 {@code /plans/{id}/copy} 后带新 id。 */
        Long planId,
        String space,
        String style,
        /** 生成张数 = 出图张数。 */
        Integer angles,
        List<PartSelection> parts,
        LayoutModel layout,
        /**
         * 投递尝试次数,最小 1。
         *
         * <p>⚠️ 契约写的是"由 Java 投递器递增",但<b>投递器做不到</b> ——
         * {@code OutboxDispatcher} 的原则是把库里存的 payload 原样发出,不重新构造。
         * 真正会递增它的只有补偿扫描(重投时 +1)。MQ 层的重传记在
         * {@code t_outbox.retry_count},两者是不同的东西,见
         * {@code openapi-verification.md} §35.5。
         */
        Integer attempt,
        /** 入队时间(ISO 8601)。 */
        String enqueuedAt) {

    /** 契约 {@code attempt} 的最小值。 */
    public static final int INITIAL_ATTEMPT = 1;

    /** 补偿扫描重投用:只动 {@code attempt},其余原样。 */
    public GenerateTaskPayload withAttempt(int nextAttempt) {
        return new GenerateTaskPayload(taskId, type, traceId, userId, planId, space, style,
                angles, parts, layout, nextAttempt, enqueuedAt);
    }
}
