package org.example.acm.mall.common;

import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "业务码")
public enum ResultCode {

    SUCCESS(0, "成功"),
    PARAM_ERROR(400, "参数不合法"),
    UNAUTHORIZED(401, "未登录或登录已过期"),
    FORBIDDEN(403, "无权访问该资源"),
    NOT_FOUND(404, "资源不存在"),
    CONFLICT(409, "资源状态冲突"),
    BIZ_ERROR(1000, "业务处理失败"),
    ALIPAY_ERROR(2000, "支付宝交互失败"),
    SYSTEM_ERROR(500, "系统繁忙");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    @JsonValue
    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
