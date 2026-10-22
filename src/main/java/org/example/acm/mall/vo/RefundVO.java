package org.example.acm.mall.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "退款结果")
public record RefundVO(
        String orderNo,
        @Schema(description = "退款请求号，重试幂等键") String outRequestNo,
        BigDecimal refundAmount,
        @Schema(description = "PROCESSING|SUCCESS|FAIL") String status,
        @Schema(description = "本次是否发生资金变动。false 通常表示该 out_request_no 已退过") Boolean fundChange,
        LocalDateTime refundedTime) {
}
