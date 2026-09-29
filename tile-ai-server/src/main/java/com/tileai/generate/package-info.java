/**
 * 生成编排模块(架构 §4.1)。
 *
 * <p>效果图生成任务的受理、<b>未选地面/墙面部位的 AI 自动补齐触发</b>、任务落库、
 * 派发 MQ、进度查询、接收 Python 回调、结果落库与自动保存触发。
 *
 * <p>可靠性采用 <b>Outbox + 补偿扫描</b>(架构 §6.4):任务与 {@code t_outbox}
 * 同事务写入,独立投递器扫描投递;回调以 {@code taskId} 幂等去重。
 * 状态机恒为 pending → running → success / failed。落地见 T-1.11。
 */
package com.tileai.generate;
