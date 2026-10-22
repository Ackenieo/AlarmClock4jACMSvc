package org.example.acm.mall.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.example.acm.mall.common.BizException;
import org.example.acm.mall.common.ResultCode;
import org.example.acm.mall.entity.MallEntitlement;
import org.example.acm.mall.entity.MallOrder;
import org.example.acm.mall.enums.EntitlementStatus;
import org.example.acm.mall.mapper.MallEntitlementMapper;
import org.example.acm.mall.mapper.MallProductMapper;
import org.example.acm.mall.vo.EntitlementVO;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 用户已解锁的虚拟内容（单词本 / 皮肤）。
 */
@Service
public class EntitlementService {

    private final MallEntitlementMapper entitlementMapper;
    private final MallProductMapper productMapper;

    public EntitlementService(MallEntitlementMapper entitlementMapper, MallProductMapper productMapper) {
        this.entitlementMapper = entitlementMapper;
        this.productMapper = productMapper;
    }

    /** 已持有有效权益时不再允许重复购买同一商品 */
    public void assertNotOwned(Long userId, Long productId) {
        MallEntitlement existing = find(userId, productId);
        if (existing != null && existing.getStatus() == EntitlementStatus.ACTIVE) {
            throw new BizException(ResultCode.CONFLICT, "已购买过该商品，无需重复下单");
        }
    }

    public MallEntitlement find(Long userId, Long productId) {
        return entitlementMapper.selectOne(Wrappers.<MallEntitlement>lambdaQuery()
                .eq(MallEntitlement::getUserId, userId)
                .eq(MallEntitlement::getProductId, productId));
    }

    /** 靠 UNIQUE(user_id, product_id) 做 upsert，重复调用是 no-op */
    public void grant(MallOrder order) {
        MallEntitlement entitlement = new MallEntitlement();
        entitlement.setUserId(order.getUserId());
        entitlement.setProductId(order.getProductId());
        entitlement.setCategory(order.getCategory());
        entitlement.setOrderId(order.getId());
        entitlement.setOutTradeNo(order.getOrderNo());
        entitlement.setStatus(EntitlementStatus.ACTIVE);
        entitlement.setGrantedTime(LocalDateTime.now());
        entitlementMapper.upsertActive(entitlement);
    }

    /** 只回收由本单发放的权益，别人更早买的不受影响 */
    public void revokeForOrder(Long orderId) {
        entitlementMapper.revokeByOrderId(orderId, LocalDateTime.now());
    }

    public List<EntitlementVO> listMine(Long userId) {
        List<MallEntitlement> entitlements = entitlementMapper.selectList(
                Wrappers.<MallEntitlement>lambdaQuery()
                        .eq(MallEntitlement::getUserId, userId)
                        .orderByDesc(MallEntitlement::getGrantedTime));
        if (entitlements.isEmpty()) {
            return List.of();
        }
        List<Long> productIds = entitlements.stream().map(MallEntitlement::getProductId).distinct().toList();
        Map<Long, String> names = productMapper.selectByIds(productIds).stream()
                .collect(Collectors.toMap(p -> p.getId(), p -> p.getName(), (a, b) -> a));
        return entitlements.stream().map(e -> toVO(e, names)).toList();
    }

    private EntitlementVO toVO(MallEntitlement e, Map<Long, String> names) {
        return new EntitlementVO(e.getProductId(), names.get(e.getProductId()),
                e.getCategory().name(), e.getCategory().getLabel(),
                e.getStatus().name(), e.getStatus().getLabel(),
                e.getOrderId(), e.getGrantedTime(), e.getRevokedTime());
    }
}
