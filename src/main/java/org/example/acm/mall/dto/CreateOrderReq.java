package org.example.acm.mall.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "下单请求（本演示一单一个商品，数量固定为 1）")
public record CreateOrderReq(

        @Schema(description = "商品 ID", example = "1")
        @NotNull(message = "商品 ID 不能为空")
        Long productId) {
}
