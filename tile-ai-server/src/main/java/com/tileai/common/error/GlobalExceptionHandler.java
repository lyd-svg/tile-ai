package com.tileai.common.error;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.tileai.common.api.ApiResponse;
import com.tileai.common.api.ErrorItem;
import com.tileai.common.api.ValidationErrors;

import jakarta.validation.ConstraintViolationException;

/**
 * 全局异常处理 —— 把所有异常收敛成统一响应体(架构 §10.1、§10.5)。
 *
 * <p><b>状态码口径</b>(T-13):一律返回 {@link ErrorCode#httpStatus()},
 * 只有 401/403 不是 200,其余业务失败(含参数校验 10001)都是 HTTP 200。
 * 这个口径只在本类与 {@link ErrorCode} 两处存在,不要在别处再设状态码。
 *
 * <p><b>校验明细的落点</b>是 {@code data.errors[]}(§10.5 原文),即 {@code data} 是
 * 对象 {@code {"errors":[...]}} 而<b>不是</b>数组 —— 见 {@link ValidationErrors}。
 *
 * <p>90001 对外只给固定文案,<b>绝不</b>把异常消息透给前端(§10.5:不暴露细节);
 * 真实原因连同 traceId 进日志,靠 traceId 关联排查。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String BODY_FIELD = "body";
    private static final String REQUEST_FIELD = "request";

    /** 业务异常 —— 错误码与文案都由抛出方指定;可携带 data(如 10001 的 errors[])。 */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Object>> handleBusiness(BusinessException ex) {
        ErrorCode code = ex.errorCode();
        // 401/403 是安全事件,记 warn 便于审计;其余业务失败属预期控制流,记 info 即可。
        if (code == ErrorCode.UNAUTHENTICATED || code == ErrorCode.TOKEN_EXPIRED
                || code == ErrorCode.FORBIDDEN) {
            log.warn("业务拒绝 code={} msg={}", code.code(), ex.getMessage());
        } else {
            log.info("业务失败 code={} msg={}", code.code(), ex.getMessage());
        }
        return build(code, ex.getMessage(), ex.data());
    }

    /** @Valid 校验失败(请求体)。 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<ValidationErrors>> handleBodyValidation(
            MethodArgumentNotValidException ex) {
        List<ErrorItem> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(GlobalExceptionHandler::toErrorItem)
                .toList();
        // 类级约束(如"两个字段不能同时为空")没有字段路径,补一个 field="request" 的条目,
        // 避免出现"10001 但 errors 为空"这种前端无从提示的响应。
        if (errors.isEmpty()) {
            errors = ex.getBindingResult().getGlobalErrors().stream()
                    .map(e -> ErrorItem.of(REQUEST_FIELD, e.getDefaultMessage()))
                    .toList();
        }
        log.info("请求体校验失败,{} 项", errors.size());
        return build(ErrorCode.PARAM_INVALID, ErrorCode.PARAM_INVALID.defaultMessage(),
                ValidationErrors.of(errors));
    }

    /** @Validated 校验失败(查询参数 / 路径变量)。 */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<ValidationErrors>> handleParamValidation(
            ConstraintViolationException ex) {
        List<ErrorItem> errors = ex.getConstraintViolations().stream()
                .map(v -> new ErrorItem(v.getPropertyPath().toString(), v.getMessage(),
                        v.getInvalidValue()))
                .toList();
        log.info("参数校验失败,{} 项", errors.size());
        return build(ErrorCode.PARAM_INVALID, ErrorCode.PARAM_INVALID.defaultMessage(),
                ValidationErrors.of(errors));
    }

    /** 请求体缺失或 JSON 语法错误。 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<ValidationErrors>> handleUnreadable(
            HttpMessageNotReadableException ex) {
        log.info("请求体不可读: {}", ex.getMessage());
        return build(ErrorCode.PARAM_INVALID, "请求体缺失或格式错误",
                ValidationErrors.of(List.of(ErrorItem.of(BODY_FIELD, "无法解析为合法 JSON"))));
    }

    /** 必填查询参数缺失。 */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<ValidationErrors>> handleMissingParam(
            MissingServletRequestParameterException ex) {
        log.info("缺少必填参数: {}", ex.getParameterName());
        return build(ErrorCode.PARAM_INVALID, ErrorCode.PARAM_INVALID.defaultMessage(),
                ValidationErrors.of(List.of(ErrorItem.of(ex.getParameterName(), "缺少必填参数"))));
    }

    /** 参数类型不匹配(如把 abc 传给 integer 的 page)。 */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<ValidationErrors>> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex) {
        log.info("参数类型不匹配: {}={}", ex.getName(), ex.getValue());
        return build(ErrorCode.PARAM_INVALID, ErrorCode.PARAM_INVALID.defaultMessage(),
                ValidationErrors.of(List.of(
                        new ErrorItem(ex.getName(), "参数类型不正确", ex.getValue()))));
    }

    /** 兜底 —— 未预期异常。对外不暴露任何细节,靠 traceId 回查日志。 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
        log.error("未预期异常(内部错误)", ex);
        return build(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.defaultMessage(), null);
    }

    private static ErrorItem toErrorItem(FieldError fe) {
        return new ErrorItem(fe.getField(), fe.getDefaultMessage(), fe.getRejectedValue());
    }

    private static <T> ResponseEntity<ApiResponse<T>> build(ErrorCode code, String message, T data) {
        return ResponseEntity.status(code.httpStatus())
                .body(ApiResponse.fail(code, message, data));
    }
}
