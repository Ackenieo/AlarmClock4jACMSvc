package org.example.acm.mall.enums;

/**
 * 支付宝交易状态原样存字符串，取值来自接口报文而非 Java 枚举命名，
 * 因此这里用常量而不是 enum，避免映射到 DB 时丢信息。
 */
public final class AlipayTradeStatus {

    public static final String WAIT_BUYER_PAY = "WAIT_BUYER_PAY";
    public static final String TRADE_SUCCESS = "TRADE_SUCCESS";
    public static final String TRADE_FINISHED = "TRADE_FINISHED";
    public static final String TRADE_CLOSED = "TRADE_CLOSED";

    private AlipayTradeStatus() {
    }

    /** TRADE_CLOSED 只有在已付款退款后才可能出现在完整生命周期里，未付的空交易用 subCode 判定 */
    public static boolean isPaid(String status) {
        return TRADE_SUCCESS.equals(status) || TRADE_FINISHED.equals(status);
    }
}
