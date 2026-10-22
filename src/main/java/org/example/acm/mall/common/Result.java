package org.example.acm.mall.common;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "统一响应包装")
public record Result<T>(
        @Schema(description = "业务码，0 表示成功") int code,
        @Schema(description = "提示信息") String message,
        @Schema(description = "业务数据，失败时为 null") T data,
        @Schema(description = "服务端时间戳（毫秒）") long timestamp) {

    public static <T> Result<T> ok(T data) {
        return new Result<>(ResultCode.SUCCESS.getCode(), "success", data, System.currentTimeMillis());
    }

    public static <T> Result<T> ok() {
        return ok(null);
    }

    public static <T> Result<T> fail(ResultCode code) {
        return new Result<>(code.getCode(), code.getMessage(), null, System.currentTimeMillis());
    }

    public static <T> Result<T> fail(ResultCode code, String message) {
        return new Result<>(code.getCode(), message, null, System.currentTimeMillis());
    }

    public static <T> Result<T> fail(int code, String message) {
        return new Result<>(code, message, null, System.currentTimeMillis());
    }
}
