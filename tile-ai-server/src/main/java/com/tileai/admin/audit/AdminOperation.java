package com.tileai.admin.audit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注一个<b>写操作</b>,由 {@link AdminAuditAspect} 记入 {@code t_operation_log}
 * (架构 §4.4:所有写操作记入)。
 *
 * <p>只加在写接口上(POST / PUT / PATCH / DELETE)。读接口不要加 ——
 * 查询不该产生审计记录,否则日志会被淹没、真正重要的变更反而看不见。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AdminOperation {

    /** 动作标识,建议 {@code <资源>.<动作>},如 {@code product.delete}。 */
    String action();

    /** 目标资源类型,如 {@code product}。 */
    String targetType();

    /**
     * 目标 ID 取自哪个<b>路径变量</b>(如 {@code id})。
     * 留空表示该操作没有单一目标(如批量导入)。
     */
    String targetIdParam() default "";
}
