package com.tileai.common.error;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;

/**
 * 业务错误码(架构 §10.5;契约 {@code openapi-common.yaml#/components/schemas/ErrorCode})。
 *
 * <p><b>状态码口径</b>(§10.1 · C5 收敛,并由契约校验表 T-13 约束):
 * 业务结果<b>统一返回 HTTP 200</b>,成败由响应体 {@code code} 区分;
 * <b>仅</b> 401(未登录/令牌无效或过期)与 403(无权限)使用真实 HTTP 状态;
 * 5xx 保留给网关/进程级故障,<b>不承载业务码</b>。
 *
 * <p>本枚举的取值集合必须与契约 enum 完全一致 —— 由
 * {@code ErrorCodeContractTest} 直接解析契约 YAML 逐值比对。
 * <b>增删改这里的常量时,必须同步改 PRD → 架构 §10.5 → 契约</b>(顺序不可颠倒)。
 */
public enum ErrorCode {

    SUCCESS(0, "ok", HttpStatus.OK),

    // ---- 1xxxx 通用 ----
    PARAM_INVALID(10001, "参数校验失败", HttpStatus.OK),
    UNAUTHENTICATED(10002, "未登录或令牌无效", HttpStatus.UNAUTHORIZED),
    TOKEN_EXPIRED(10003, "令牌过期,需刷新", HttpStatus.UNAUTHORIZED),
    FORBIDDEN(10004, "无权限", HttpStatus.FORBIDDEN),
    RATE_LIMITED(10005, "请求过于频繁", HttpStatus.OK),

    // ---- 2xxxx 账户 ----
    // 注:v0.7 起登录改为「用户名 + 密码」,原 20001(验证码错误或过期)、
    // 20002(验证码发送过于频繁)已随短信通道一并取消;20003 由「手机号已注册」
    // 改义为「用户名已注册」。见架构 §15.6。
    USERNAME_ALREADY_REGISTERED(20003, "用户名已注册", HttpStatus.OK),
    PASSWORD_FORMAT_INVALID(20004, "密码格式不合规", HttpStatus.OK),
    ACCOUNT_OR_PASSWORD_WRONG(20005, "账号或密码错误", HttpStatus.OK),

    // ---- 3xxxx 资源与状态 ----
    NOT_FOUND(30001, "资源不存在", HttpStatus.OK),
    STATE_NOT_ALLOWED(30002, "状态不允许该操作", HttpStatus.OK),

    // ---- 4xxxx 生成任务 ----
    GENERATE_FAILED(40001, "生成任务失败", HttpStatus.OK),
    GENERATE_BUSY(40002, "生成服务繁忙", HttpStatus.OK),
    TASK_NOT_FOUND(40003, "任务不存在", HttpStatus.OK),
    COMPLETE_PARTS_FAILED(40004, "部位补齐失败", HttpStatus.OK),

    // ---- 5xxxx 智能体 ----
    AI_OUT_OF_SCOPE(50001, "AI 咨询超出范围", HttpStatus.OK),

    // ---- 9xxxx 系统 ----
    INTERNAL_ERROR(90001, "系统内部错误", HttpStatus.OK);

    private final int code;
    private final String defaultMessage;
    private final HttpStatus httpStatus;

    ErrorCode(int code, String defaultMessage, HttpStatus httpStatus) {
        this.code = code;
        this.defaultMessage = defaultMessage;
        this.httpStatus = httpStatus;
    }

    public int code() {
        return code;
    }

    public String defaultMessage() {
        return defaultMessage;
    }

    /**
     * 该业务码对应的真实 HTTP 状态。
     * <p>只有 401/403 两个码不是 200 —— 这是契约 T-13 的硬要求。
     */
    public HttpStatus httpStatus() {
        return httpStatus;
    }

    private static final Map<Integer, ErrorCode> BY_CODE =
            Arrays.stream(values()).collect(Collectors.toUnmodifiableMap(ErrorCode::code, c -> c));

    /**
     * 按业务码查找。未知码返回空。
     *
     * <p>用于判断"别处传来的码是不是**已定义的**" —— 典型场景是内部服务
     * (Python 智能体)返回了一个码,Java 要决定该透传还是折算成 90001。
     * 架构 §10.4 约定:**只透传已定义业务码**,其余折 90001 ——
     * 否则内部实现细节(甚至第三方 SDK 的错误码)会漏到用户界面。
     */
    public static Optional<ErrorCode> of(int code) {
        return Optional.ofNullable(BY_CODE.get(code));
    }
}
