package org.example.acm.mall.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.acm.mall.common.BizException;
import org.example.acm.mall.common.PageResult;
import org.example.acm.mall.common.ResultCode;
import org.example.acm.mall.entity.MallProduct;
import org.example.acm.mall.enums.ProductCategory;
import org.example.acm.mall.mapper.MallProductMapper;
import org.example.acm.mall.vo.ProductVO;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ProductService {

    private final MallProductMapper productMapper;

    public ProductService(MallProductMapper productMapper) {
        this.productMapper = productMapper;
    }

    public PageResult<ProductVO> page(long page, long size, ProductCategory category, String keyword) {
        LambdaQueryWrapper<MallProduct> query = Wrappers.<MallProduct>lambdaQuery()
                .eq(MallProduct::getOnSale, 1)
                .eq(category != null, MallProduct::getCategory, category)
                .like(StringUtils.hasText(keyword), MallProduct::getName, keyword)
                .orderByAsc(MallProduct::getSortNo)
                .orderByAsc(MallProduct::getId);
        Page<MallProduct> result = productMapper.selectPage(new Page<>(page, size), query);
        return PageResult.of(result, ProductService::toVO);
    }

    public ProductVO detail(Long id) {
        return toVO(get(id));
    }

    /** 下单前用：不存在或已下架都不可购买 */
    public MallProduct getSellable(Long id) {
        MallProduct product = get(id);
        if (product.getOnSale() == null || product.getOnSale() != 1) {
            throw new BizException(ResultCode.CONFLICT, "商品已下架：" + product.getName());
        }
        return product;
    }

    public MallProduct get(Long id) {
        MallProduct product = productMapper.selectById(id);
        if (product == null) {
            throw new BizException(ResultCode.NOT_FOUND, "商品不存在：" + id);
        }
        return product;
    }

    static ProductVO toVO(MallProduct product) {
        return new ProductVO(product.getId(), product.getName(), product.getDescription(),
                product.getCategory().name(), product.getCategory().getLabel(),
                product.getPrice(), product.getOriginalPrice(), product.getCoverUrl(),
                product.getWordCount(), product.getSkinStyle());
    }
}
