package com.tileai.admin.audit;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.HandlerMapping;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tileai.admin.entity.OperationLog;
import com.tileai.admin.mapper.OperationLogMapper;
import com.tileai.common.security.AuthPrincipal;
import com.tileai.common.security.CurrentPrincipal;
import com.tileai.common.security.SubjectType;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 把标注了 {@link AdminOperation} 的写操作记入 {@code t_operation_log}(架构 §4.4)。
 *
 * <h2>两个必须说明的取舍</h2>
 *
 * <p><b>① 只记成功的操作。</b>审计记录里的 {@code targetId} 指向一个真实存在的资源;
 * 若失败的操作也记,会留下一堆指向不存在资源的记录,排查时反而干扰。
 * 代价:被拒绝的越权尝试、校验失败的写请求<b>不会</b>出现在操作日志里
 * (它们仍留有接口访问日志与 traceId)。若日后需要"失败也要留痕",
 * 应新增独立的尝试日志,而不是污染这张表。
 *
 * <p><b>② 记日志失败不回滚业务。</b>本切面在业务方法<b>返回之后</b>写日志,
 * 此时业务事务已提交。若此处抛异常,会出现"数据改了但接口报错"的更坏结果,
 * 所以只记错误日志、不向上抛。代价:极端情况下可能"改了但没留痕"。
 * 要做到严格一致,需把审计写进与业务<b>同一个事务</b>(参见 §6.4 的 Outbox 思路),
 * 那属于 T-2.x 的加固范围。
 */
@Aspect
@Component
public class AdminAuditAspect {

    private static final Logger log = LoggerFactory.getLogger(AdminAuditAspect.class);

    private final OperationLogMapper operationLogMapper;
    private final ObjectMapper objectMapper;

    public AdminAuditAspect(OperationLogMapper operationLogMapper, ObjectMapper objectMapper) {
        this.operationLogMapper = operationLogMapper;
        this.objectMapper = objectMapper;
    }

    @Around("@annotation(operation)")
    public Object around(ProceedingJoinPoint joinPoint, AdminOperation operation) throws Throwable {
        Object result = joinPoint.proceed();
        try {
            record(operation);
        } catch (Exception e) {
            log.error("写操作日志失败: action={} —— 业务已生效但未留痕,需人工核查",
                    operation.action(), e);
        }
        return result;
    }

    private void record(AdminOperation operation) {
        Optional<AuthPrincipal> principal = CurrentPrincipal.find();
        if (principal.isEmpty() || principal.get().type() != SubjectType.ADMIN) {
            // 写接口本应已通过 ROLE_ADMIN,走到这里说明配置有洞 —— 记 warn 而不是静默。
            log.warn("写操作 {} 未取到后台身份,未记入操作日志", operation.action());
            return;
        }

        HttpServletRequest request = currentRequest();

        OperationLog entity = new OperationLog();
        entity.setAdminId(principal.get().id());
        entity.setAction(operation.action());
        entity.setTargetType(operation.targetType());
        entity.setTargetId(resolveTargetId(request, operation));
        entity.setIp(clientIp(request));
        entity.setDetail(detail(request));
        operationLogMapper.insert(entity);
    }

    /** 目标 ID 取自注解指定的路径变量。 */
    private static String resolveTargetId(HttpServletRequest request, AdminOperation operation) {
        if (request == null || !StringUtils.hasText(operation.targetIdParam())) {
            return null;
        }
        Object attribute = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (attribute instanceof Map<?, ?> variables) {
            Object value = variables.get(operation.targetIdParam());
            return value == null ? null : value.toString();
        }
        return null;
    }

    /**
     * 取客户端 IP。
     *
     * <p>生产环境请求经 Nginx 转发(§12.1),{@code remoteAddr} 会是网关地址,
     * 真实来源在 {@code X-Forwarded-For} 的首段。该头可被伪造,
     * 因此它只用于审计参考,<b>不得</b>作为安全判据。
     */
    private static String clientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * 详情只记"请求动作本身"。
     *
     * <p><b>刻意不记请求体</b> —— 请求体里可能有密码(如账号管理接口),
     * 一旦落库就成了明文凭据。具体业务若需要更细的审计信息,
     * 应在各自的接口里显式提供安全字段,而不是在这里整包 dump。
     */
    private String detail(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        Map<String, String> detail = new LinkedHashMap<>();
        detail.put("method", request.getMethod());
        detail.put("path", request.getRequestURI());
        try {
            return objectMapper.writeValueAsString(detail);
        } catch (JsonProcessingException e) {
            // 两个字符串的序列化基本不可能失败;真失败了也不能因此丢掉整条审计记录。
            log.debug("操作详情序列化失败,detail 置空", e);
            return null;
        }
    }

    private static HttpServletRequest currentRequest() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs
                ? attrs.getRequest()
                : null;
    }
}
