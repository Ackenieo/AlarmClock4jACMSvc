package org.example.acm.mall.common;

import com.alipay.api.AlipayApiException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BizException.class)
    public Result<Void> handleBiz(BizException ex) {
        log.warn("业务异常 code={} msg={}", ex.getCode(), ex.getMessage());
        return Result.fail(ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public Result<Void> handleValidation(BindException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return Result.fail(ResultCode.PARAM_ERROR, detail);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public Result<Void> handleConstraint(ConstraintViolationException ex) {
        String detail = ex.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining("; "));
        return Result.fail(ResultCode.PARAM_ERROR, detail);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class, IllegalArgumentException.class})
    public Result<Void> handleBadRequest(Exception ex) {
        return Result.fail(ResultCode.PARAM_ERROR, ex.getMessage());
    }

    @ExceptionHandler(AlipayApiException.class)
    public Result<Void> handleAlipay(AlipayApiException ex) {
        log.error("支付宝 SDK 异常", ex);
        return Result.fail(ResultCode.ALIPAY_ERROR, "支付宝交互异常：" + ex.getErrMsg());
    }

    // NoResourceFoundException 覆盖了 Spring Boot 3 里 NoHandlerFoundException 的场景
    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Result<Void> handleNotFound(NoResourceFoundException ex) {
        return Result.fail(ResultCode.NOT_FOUND, "接口不存在：" + ex.getResourcePath());
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleUnexpected(Exception ex) {
        log.error("未预期异常", ex);
        return Result.fail(ResultCode.SYSTEM_ERROR, "系统繁忙，请稍后再试");
    }
}
