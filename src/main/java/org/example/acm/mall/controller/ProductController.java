package org.example.acm.mall.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.example.acm.mall.common.PageResult;
import org.example.acm.mall.common.Result;
import org.example.acm.mall.enums.ProductCategory;
import org.example.acm.mall.service.ProductService;
import org.example.acm.mall.vo.ProductVO;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "02-商品", description = "单词本与应用皮肤列表，公开可访问")
@Validated
@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @Operation(summary = "商品分页列表", description = "只返回在售商品，可按类目与名称关键字过滤")
    @GetMapping
    public Result<PageResult<ProductVO>> page(
            @Parameter(description = "页码，从 1 开始") @RequestParam(defaultValue = "1") @Min(1) long page,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") @Min(1) @Max(100) long size,
            @Parameter(description = "类目：WORD_BOOK|APP_SKIN，不传则全部")
            @RequestParam(required = false) ProductCategory category,
            @Parameter(description = "商品名关键字") @RequestParam(required = false) String keyword) {
        return Result.ok(productService.page(page, size, category, keyword));
    }

    @Operation(summary = "商品详情")
    @GetMapping("/{id}")
    public Result<ProductVO> detail(@PathVariable Long id) {
        return Result.ok(productService.detail(id));
    }
}
