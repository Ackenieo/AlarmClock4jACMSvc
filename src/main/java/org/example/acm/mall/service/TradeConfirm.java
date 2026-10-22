package org.example.acm.mall.service;

import java.time.LocalDateTime;

/**
 * 支付宝侧确认交易成功的证据，来源可以是异步通知，也可以是主动查单。
 * 两个来源最终都汇到 OrderService#markPaid 这一个幂等入口。
 */
public record TradeConfirm(
        String outTradeNo,
        String tradeNo,
        String tradeStatus,
        String buyerUserId,
        String totalAmount,
        LocalDateTime gmtPayment,
        String notifyContent) {

    /** 主动查单来源没有通知原文 */
    public static TradeConfirm fromQuery(String outTradeNo, String tradeNo, String tradeStatus,
                                         String buyerUserId, String totalAmount, LocalDateTime gmtPayment) {
        return new TradeConfirm(outTradeNo, tradeNo, tradeStatus, buyerUserId, totalAmount, gmtPayment, null);
    }
}
