package org.example.acm.mall.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "主动查单同步结果")
public record PaySyncVO(
        String orderNo,
        @Schema(description = "支付宝交易号，未交易时为 null") String tradeNo,
        @Schema(description = "支付宝侧状态：WAIT_BUYER_PAY|TRADE_SUCCESS|TRADE_FINISHED|TRADE_CLOSED") String tradeStatus,
        @Schema(description = "本地订单状态") String orderStatus,
        @Schema(description = "本次调用是否真的把订单从待支付改成了已支付。第二次同步为 false，即幂等的可见证据")
        boolean changed,
        @Schema(description = "支付宝付款时间") LocalDateTime paidTime,
        String message) {
}
