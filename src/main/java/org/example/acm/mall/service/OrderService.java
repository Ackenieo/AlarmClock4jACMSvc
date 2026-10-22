package org.example.acm.mall.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.acm.mall.common.BizException;
import org.example.acm.mall.common.PageResult;
import org.example.acm.mall.common.ResultCode;
import org.example.acm.mall.entity.MallOrder;
import org.example.acm.mall.entity.MallPayment;
import org.example.acm.mall.entity.MallProduct;
import org.example.acm.mall.enums.AlipayTradeStatus;
import org.example.acm.mall.enums.OrderStatus;
import org.example.acm.mall.enums.ProductCategory;
import org.example.acm.mall.mapper.MallOrderMapper;
import org.example.acm.mall.mapper.MallPaymentMapper;
import org.example.acm.mall.config.AppProperties;
import org.example.acm.mall.vo.OrderVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);
    private static final DateTimeFormatter ORDER_NO_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final MallOrderMapper orderMapper;
    private final MallPaymentMapper paymentMapper;
    private final ProductService productService;
    private final EntitlementService entitlementService;
    private final AppProperties appProperties;

    public OrderService(MallOrderMapper orderMapper,
                        MallPaymentMapper paymentMapper,
                        ProductService productService,
                        EntitlementService entitlementService,
                        AppProperties appProperties) {
        this.orderMapper = orderMapper;
        this.paymentMapper = paymentMapper;
        this.productService = productService;
        this.entitlementService = entitlementService;
        this.appProperties = appProperties;
    }

    public MallOrder create(Long userId, Long productId) {
        MallProduct product = productService.getSellable(productId);
        entitlementService.assertNotOwned(userId, productId);

        MallOrder order = new MallOrder();
        order.setUserId(userId);
        order.setProductId(product.getId());
        // 价格与名称做快照：商品后续改价或下架都不该影响已下的单
        order.setProductName(product.getName());
        order.setCategory(product.getCategory());
        order.setUnitPrice(product.getPrice());
        order.setQuantity(1);
        order.setPayAmount(product.getPrice().setScale(2, RoundingMode.HALF_UP));
        order.setStatus(OrderStatus.WAITING_PAY);
        order.setExpireTime(LocalDateTime.now().plusMinutes(appProperties.getOrder().getExpireMinutes()));
        for (int attempt = 0; attempt < 3; attempt++) {
            order.setOrderNo(generateOrderNo());
            try {
                orderMapper.insert(order);
                return order;
            } catch (DuplicateKeyException ex) {
                order.setId(null);
                log.warn("订单号冲突，重试第 {} 次", attempt + 1);
            }
        }
        throw new BizException(ResultCode.SYSTEM_ERROR, "订单号生成失败，请重试");
    }

    /**
     * 幂等的支付确认。异步通知与主动查单都会走到这里，靠带前置条件的 UPDATE 保证只发一次货。
     * 事务内绝不调用支付宝 SDK，这里是纯本地写库。
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean markPaid(MallOrder order, TradeConfirm confirm) {
        LocalDateTime paidTime = confirm.gmtPayment() == null ? LocalDateTime.now() : confirm.gmtPayment();
        int rows = orderMapper.transitStatus(order.getId(), OrderStatus.WAITING_PAY.name(),
                OrderStatus.PAID.name(), paidTime);
        if (rows == 0) {
            log.info("订单 {} 未处于待支付或已被处理，跳过发货", order.getOrderNo());
            return false;
        }
        entitlementService.grant(order);
        recordTradeConfirmation(order, confirm, paidTime);
        log.info("订单 {} 支付确认完成，已解锁商品 {}", order.getOrderNo(), order.getProductId());
        return true;
    }

    private void recordTradeConfirmation(MallOrder order, TradeConfirm confirm, LocalDateTime paidTime) {
        MallPayment payment = findPayment(order.getOrderNo());
        boolean isNew = payment == null;
        if (isNew) {
            payment = new MallPayment();
            payment.setOrderId(order.getId());
            payment.setOutTradeNo(order.getOrderNo());
            payment.setChannel("ALIPAY");
            payment.setTotalAmount(order.getPayAmount());
            payment.setRefundAmount(BigDecimal.ZERO);
        }
        payment.setTradeNo(confirm.tradeNo());
        payment.setTradeStatus(confirm.tradeStatus());
        payment.setBuyerUserId(confirm.buyerUserId());
        payment.setGmtPayment(paidTime);
        payment.setLastSyncTime(LocalDateTime.now());
        if (confirm.notifyContent() != null) {
            payment.setNotifyContent(confirm.notifyContent());
        }
        if (isNew) {
            insertPaymentOrRejectDuplicateTrade(payment, confirm);
        } else {
            paymentMapper.updateById(payment);
        }
    }

    /** 已有流水时补上交易号等确认信息 */
    public void upsertPayment(MallPayment payment) {
        if (payment.getId() == null) {
            paymentMapper.insert(payment);
        } else {
            paymentMapper.updateById(payment);
        }
    }

    private void insertPaymentOrRejectDuplicateTrade(MallPayment payment, TradeConfirm confirm) {
        try {
            paymentMapper.insert(payment);
        } catch (DuplicateKeyException ex) {
            // uk_pay_trade_no 命中：同一笔支付宝交易试图支付另一个订单，属于异常报文，必须人工介入
            throw BizException.of(ResultCode.BIZ_ERROR,
                    "交易号 " + confirm.tradeNo() + " 已关联其它订单，拒绝重复入账");
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean markClosed(MallOrder order) {
        LocalDateTime now = LocalDateTime.now();
        return orderMapper.transitStatus(order.getId(), OrderStatus.WAITING_PAY.name(),
                OrderStatus.CLOSED.name(), now) == 1;
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean markRefunded(MallOrder order, BigDecimal refundAmount) {
        LocalDateTime now = LocalDateTime.now();
        int rows = orderMapper.transitStatus(order.getId(), OrderStatus.PAID.name(),
                OrderStatus.REFUNDED.name(), now);
        if (rows == 0) {
            return false;
        }
        entitlementService.revokeForOrder(order.getId());
        MallPayment payment = findPayment(order.getOrderNo());
        if (payment != null) {
            payment.setRefundAmount(refundAmount);
            payment.setTradeStatus(AlipayTradeStatus.TRADE_CLOSED);
            paymentMapper.updateById(payment);
        }
        return true;
    }

    public MallOrder requireOwned(Long userId, Long orderId) {
        MallOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BizException(ResultCode.NOT_FOUND, "订单不存在：" + orderId);
        }
        if (!order.getUserId().equals(userId)) {
            // 不返回「不存在」，避免探测他人订单 ID 是否存在；也不泄露任何订单内容
            throw new BizException(ResultCode.FORBIDDEN, "无权访问该订单");
        }
        return order;
    }

    public MallOrder findByOrderNo(String orderNo) {
        MallOrder order = orderMapper.selectOne(Wrappers.<MallOrder>lambdaQuery()
                .eq(MallOrder::getOrderNo, orderNo));
        if (order == null) {
            throw new BizException(ResultCode.NOT_FOUND, "订单不存在：" + orderNo);
        }
        return order;
    }

    public MallPayment findPayment(String orderNo) {
        return paymentMapper.selectOne(Wrappers.<MallPayment>lambdaQuery()
                .eq(MallPayment::getOutTradeNo, orderNo));
    }

    public PageResult<OrderVO> pageOfUser(Long userId, long page, long size, OrderStatus status) {
        Page<MallOrder> result = orderMapper.selectPage(new Page<>(page, size),
                Wrappers.<MallOrder>lambdaQuery()
                        .eq(MallOrder::getUserId, userId)
                        .eq(status != null, MallOrder::getStatus, status)
                        .orderByDesc(MallOrder::getId));
        return PageResult.of(result, OrderService::toVO);
    }

    public java.util.List<MallOrder> findExpiredWaitingPay(int limit) {
        return orderMapper.selectExpiredWaitingPay(LocalDateTime.now(), limit);
    }

    /** 支付宝要求金额是最多两位小数的元字符串 */
    public static String toAlipayAmount(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String generateOrderNo() {
        // 毫秒 + 4 位随机数，长度 24（支付宝 out_trade_no 上限 64）。
        // 真正的唯一性由 uk_order_no 保证，重复时由 create() 重试
        return "ACM" + LocalDateTime.now().format(ORDER_NO_FORMAT)
                + ThreadLocalRandom.current().nextInt(1000, 10000);
    }

    public static OrderVO toVO(MallOrder order) {
        ProductCategory category = order.getCategory();
        OrderStatus status = order.getStatus();
        return new OrderVO(order.getId(), order.getOrderNo(), order.getProductId(), order.getProductName(),
                category.name(), category.getLabel(), order.getPayAmount(), order.getQuantity(),
                status.name(), status.getLabel(), order.getCreateTime(),
                order.getExpireTime(), order.getPaidTime());
    }
}
