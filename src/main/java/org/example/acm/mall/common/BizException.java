package org.example.acm.mall.common;

public class BizException extends RuntimeException {

    private final int code;

    public BizException(ResultCode resultCode) {
        this(resultCode, resultCode.getMessage());
    }

    public BizException(ResultCode resultCode, String message) {
        super(message);
        this.code = resultCode.getCode();
    }

    public static BizException of(ResultCode resultCode, String message) {
        return new BizException(resultCode, message);
    }

    public int getCode() {
        return code;
    }
}
