package com.tileai.generate.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tileai.generate.entity.GenerationTask;

/**
 * {@code t_generation_task} 的数据访问。
 *
 * <p>状态迁移方法都是<b>带条件的单条 UPDATE</b>,不是"先查再写"。这是回调幂等
 * 的全部实现,理由见 {@link #claimSuccess}。放进 mapper 而不是 service,是因为条件
 * 必须是 SQL 的 {@code WHERE} —— 一旦条件写在 Java 里,并发就绕过去了。
 */
@Mapper
public interface GenerationTaskMapper extends BaseMapper<GenerationTask> {

    /**
     * 开工:置 {@code running} 并更新进度。
     *
     * <p>{@code progress} 用 {@code GREATEST} 取大值 —— 进度回调可能乱序到达
     * (多张图各自回调),不能让"第 1 张的 25%"盖掉"第 2 张的 50%"。
     * 用户看到的进度条倒退比不动更让人怀疑系统坏了。
     *
     * @return 影响行数;0 表示任务不存在或已是 {@code success}
     */
    @Update("""
            UPDATE t_generation_task
               SET status = 'running',
                   progress = GREATEST(progress, #{progress})
             WHERE task_id = #{taskId}
               AND status <> 'success'
            """)
    int markRunning(@Param("taskId") String taskId, @Param("progress") int progress);

    /**
     * 抢占"这一次 {@code success}"。
     *
     * <p>{@code WHERE task_id = ? AND status <> 'success'} 是<b>幂等闸门</b>:
     * <ul>
     *   <li>重复回调 → 第一次已把状态改成 {@code success},第二次匹配 0 行,
     *       调用方据此判断"这次不是真正的那一次",直接返回、不做方案保存;</li>
     *   <li>并发回调 → InnoDB 对这条 UPDATE 是<b>当前读</b>(不是快照读),
     *       后到者会等前一个事务提交、拿到锁后<b>重新求值 WHERE</b>,因此同样看到
     *       {@code success} 并匹配 0 行。这就是"重复回调不产生副作用"能在并发下
     *       成立的原因 —— 而不只是顺序重复时成立。</li>
     * </ul>
     * 若改成"先 SELECT 判断,再 UPDATE",上面第二条就没了:两个事务会同时读到
     * {@code running},然后各存一份方案。
     *
     * <p><b>为什么这里不写 {@code plan_id}</b>:新建方案时,方案 id 要等方案行插进去
     * 才知道,而抢闸门必须发生在插方案<b>之前</b> —— 顺序反过来的话,抢输了才发现
     * 方案已经插了,那份方案就回不掉了。所以先抢,再由 {@link #bindPlan} 回填。
     * 覆盖场景下 id 本来就有,但也走同一条回填路径,免得出现两种写法。
     *
     * @param resultRef 封面图 url({@code t_plan_image} 里 {@code is_cover=1} 那张)
     * @return 影响行数;1 表示抢到了这一次,调用方继续做方案保存
     */
    @Update("""
            UPDATE t_generation_task
               SET status = 'success',
                   progress = 100,
                   result_ref = #{resultRef},
                   error = NULL
             WHERE task_id = #{taskId}
               AND status <> 'success'
            """)
    int claimSuccess(@Param("taskId") String taskId, @Param("resultRef") String resultRef);

    /**
     * 把方案保存产生的 id 回填到任务上。
     *
     * <p>紧跟在 {@link #claimSuccess} 与方案保存之后,同一事务。带
     * {@code status = 'success'} 条件不是多余的:没有它,一个已经成功、又被并发
     * 重投改写成 {@code running} 的任务会被这次回填写上别人的方案 id。
     *
     * @return 影响行数;理论上恒为 1
     */
    @Update("""
            UPDATE t_generation_task
               SET plan_id = #{planId}
             WHERE task_id = #{taskId}
               AND status = 'success'
            """)
    int bindPlan(@Param("taskId") String taskId, @Param("planId") Long planId);

    /**
     * 失败落库。
     *
     * <p>{@code progress} 只前进不回退(同 {@link #markRunning});{@code error}
     * 直接覆盖 —— 最后一次失败的原因才是要显示给用户的那条。
     *
     * <p>不封终态:补偿扫描重投后会回到 {@code running}(契约:
     * 「failed 可重试回 running」)。
     */
    @Update("""
            UPDATE t_generation_task
               SET status = 'failed',
                   progress = GREATEST(progress, #{progress}),
                   error = #{error}
             WHERE task_id = #{taskId}
               AND status <> 'success'
            """)
    int markFailed(@Param("taskId") String taskId,
                   @Param("progress") int progress,
                   @Param("error") String error);

    /**
     * 补偿扫描重投后回写 payload 里的 {@code attempt}。
     *
     * <p>单独一条方法、且<b>只改 payload</b>:补偿扫描要做的是"把消息再发一次",
     * 不改状态机(状态仍由 Python 的进度回调驱动,见
     * {@code openapi-verification.md} §35.2)。把它写成通用 updateById 会在
     * "读-改-写"之间引入覆盖并发更新的风险。
     */
    @Update("""
            UPDATE t_generation_task
               SET payload = #{payload}
             WHERE task_id = #{taskId}
               AND status <> 'success'
            """)
    int updatePayload(@Param("taskId") String taskId, @Param("payload") String payload);
}
