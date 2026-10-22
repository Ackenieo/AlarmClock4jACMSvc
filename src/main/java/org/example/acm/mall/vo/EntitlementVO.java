package org.example.acm.mall.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "用户已解锁的虚拟内容")
public record EntitlementVO(
        Long productId,
        String productName,
        String category,
        String categoryLabel,
        @Schema(description = "ACTIVE|REVOKED") String status,
        String statusLabel,
        @Schema(description = "由哪个订单发放") Long orderId,
        LocalDateTime grantedTime,
        LocalDateTime revokedTime) {
}
