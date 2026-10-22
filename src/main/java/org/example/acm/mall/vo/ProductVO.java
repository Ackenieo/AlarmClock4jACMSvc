package org.example.acm.mall.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "商品")
public record ProductVO(
        Long id,
        String name,
        String description,
        @Schema(description = "类目：WORD_BOOK|APP_SKIN") String category,
        String categoryLabel,
        @Schema(description = "售价（元）") BigDecimal price,
        @Schema(description = "划线原价（元）") BigDecimal originalPrice,
        String coverUrl,
        @Schema(description = "单词数，仅单词本") Integer wordCount,
        @Schema(description = "皮肤风格标识，仅皮肤") String skinStyle) {
}
