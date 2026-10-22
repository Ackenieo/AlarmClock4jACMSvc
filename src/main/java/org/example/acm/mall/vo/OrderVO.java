package org.example.acm.mall.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "订单")
public record OrderVO(
        Long id,
        @Schema(description = "商户订单号，等于支付宝 out_trade_no") String orderNo,
        Long productId,
        @Schema(description = "下单时的商品名快照") String productName,
        String category,
        String categoryLabel,
        @Schema(description = "应付金额（元）") BigDecimal payAmount,
        Integer quantity,
        @Schema(description = "WAITING_PAY|PAID|CLOSED|REFUNDED") String status,
        String statusLabel,
        LocalDateTime createTime,
        @Schema(description = "超时时间，过期后可能被关单") LocalDateTime expireTime,
        LocalDateTime paidTime) {
}
