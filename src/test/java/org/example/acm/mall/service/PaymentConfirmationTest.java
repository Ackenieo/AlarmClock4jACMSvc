package org.example.acm.mall.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.example.acm.mall.common.BizException;
import org.example.acm.mall.dto.RegisterReq;
import org.example.acm.mall.entity.MallEntitlement;
import org.example.acm.mall.entity.MallOrder;
import org.example.acm.mall.entity.MallPayment;
import org.example.acm.mall.entity.MallUser;
import org.example.acm.mall.enums.AlipayTradeStatus;
import org.example.acm.mall.enums.EntitlementStatus;
import org.example.acm.mall.enums.OrderStatus;
import org.example.acm.mall.enums.ProductCategory;
import org.example.acm.mall.mapper.MallEntitlementMapper;
import org.example.acm.mall.mapper.MallOrderMapper;
import org.example.acm.mall.mapper.MallPaymentMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 支付确认与权益回收的落库验证。
 * 支付宝异步回调进不了 localhost，沙箱凭证又还是占位符，所以 sync/notify 的真实调用跑不通；
 * 这里把 TradeConfirm 直接喂给幂等入口 markPaid，覆盖唯一没被 HTTP 验过的那两段。
 * 类上的 @Transactional 让每个用例回滚，不会污染演示数据。
 */
@SpringBootTest
@Transactional
@DisplayName("支付确认与权益发放")
class PaymentConfirmationTest {

    private static final long WORD_BOOK = 1L;
    private static final long ANOTHER_PRODUCT = 2L;

    @Autowired
    private UserService userService;
    @Autowired
    private OrderService orderService;
    @Autowired
    private EntitlementService entitlementService;
    @Autowired
    private MallOrderMapper orderMapper;
    @Autowired
    private MallPaymentMapper paymentMapper;
    @Autowired
    private MallEntitlementMapper entitlementMapper;

    private MallUser buyer;

    @BeforeEach
    void registerBuyer() {
        buyer = userService.register(new RegisterReq(
                "pay-test-" + System.nanoTime() + "@test.com", "Passw0rd", "pay-test"));
    }

    @Test
    @DisplayName("支付确认：订单转 PAID、解锁权益、补全支付流水；重复确认一个字节都不改")
    void markPaidGrantsEntitlementAndIsIdempotent() {
        MallOrder order = orderService.create(buyer.getId(), WORD_BOOK);

        // gmtPayment 传 null 走「兜底用本地时间」这条分支
        assertThat(orderService.markPaid(order, confirm(order, "T-0001", null))).isTrue();

        MallOrder paid = orderMapper.selectById(order.getId());
        assertThat(paid.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(paid.getPaidTime()).isNotNull();
        assertThat(paid.getClosedTime()).isNull();
        assertThat(paid.getRefundedTime()).isNull();

        MallEntitlement granted = entitlementService.find(buyer.getId(), WORD_BOOK);
        assertThat(granted.getStatus()).isEqualTo(EntitlementStatus.ACTIVE);
        assertThat(granted.getOrderId()).isEqualTo(order.getId());
        assertThat(granted.getOutTradeNo()).isEqualTo(order.getOrderNo());
        assertThat(granted.getRevokedTime()).isNull();

        MallPayment payment = orderService.findPayment(order.getOrderNo());
        assertThat(payment.getTradeNo()).isEqualTo("T-0001");
        assertThat(payment.getTradeStatus()).isEqualTo(AlipayTradeStatus.TRADE_SUCCESS);
        assertThat(payment.getTotalAmount()).isEqualByComparingTo("6.66");
        assertThat(payment.getBuyerUserId()).isEqualTo("2088301234567890");

        // 模拟 notify 与 sync 撞上：第二次确认必须是 no-op
        LocalDateTime paidTime = paid.getPaidTime();
        assertThat(orderService.markPaid(order, confirm(order, "T-0002", null))).isFalse();

        MallOrder again = orderMapper.selectById(order.getId());
        assertThat(again.getPaidTime()).isEqualTo(paidTime);
        assertThat(entitlementService.find(buyer.getId(), WORD_BOOK).getOrderId()).isEqualTo(order.getId());
        assertThat(paymentMapper.selectCount(Wrappers.<MallPayment>lambdaQuery()
                .eq(MallPayment::getTradeNo, "T-0002"))).isZero();
    }

    @Test
    @DisplayName("时间往返：支付宝报文里的支付时间存进去再取出来不差 8 小时")
    void gmtPaymentSurvivesTimezoneRoundTrip() {
        LocalDateTime alipayPaidTime = LocalDateTime.of(2026, 10, 21, 19, 5, 7);
        MallOrder order = orderService.create(buyer.getId(), WORD_BOOK);

        orderService.markPaid(order, confirm(order, "T-0100", alipayPaidTime));

        assertThat(orderMapper.selectById(order.getId()).getPaidTime()).isEqualTo(alipayPaidTime);
        assertThat(orderService.findPayment(order.getOrderNo()).getGmtPayment()).isEqualTo(alipayPaidTime);
    }

    @Test
    @DisplayName("/pay 已经写过流水时，确认支付是 UPDATE 而不是插入第二条")
    void markPaidFillsThePaymentRowLeftByInitPay() {
        MallOrder order = orderService.create(buyer.getId(), WORD_BOOK);
        MallPayment attempt = new MallPayment();
        attempt.setOrderId(order.getId());
        attempt.setOutTradeNo(order.getOrderNo());
        attempt.setChannel("ALIPAY");
        attempt.setTradeStatus(AlipayTradeStatus.WAIT_BUYER_PAY);
        attempt.setTotalAmount(order.getPayAmount());
        attempt.setRefundAmount(BigDecimal.ZERO);
        paymentMapper.insert(attempt);

        orderService.markPaid(order, confirm(order, "T-0200", null));

        assertThat(paymentMapper.selectCount(Wrappers.<MallPayment>lambdaQuery()
                .eq(MallPayment::getOutTradeNo, order.getOrderNo()))).isEqualTo(1L);
        MallPayment payment = paymentMapper.selectById(attempt.getId());
        assertThat(payment.getTradeNo()).isEqualTo("T-0200");
        assertThat(payment.getTradeStatus()).isEqualTo(AlipayTradeStatus.TRADE_SUCCESS);
    }

    @Test
    @DisplayName("已关闭的订单是终态：迟到的支付确认不发货")
    void lateConfirmationOnClosedOrderGrantsNothing() {
        MallOrder order = orderService.create(buyer.getId(), WORD_BOOK);
        assertThat(orderService.markClosed(order)).isTrue();
        // 重复关闭同样返回 false，条件 UPDATE 只让第一个赢家通过
        assertThat(orderService.markClosed(order)).isFalse();

        assertThat(orderService.markPaid(order, confirm(order, "T-0300", null))).isFalse();

        MallOrder closed = orderMapper.selectById(order.getId());
        assertThat(closed.getStatus()).isEqualTo(OrderStatus.CLOSED);
        assertThat(closed.getPaidTime()).isNull();
        assertThat(closed.getClosedTime()).isNotNull();
        assertThat(entitlementService.find(buyer.getId(), WORD_BOOK)).isNull();
    }

    @Test
    @DisplayName("退款：订单转 REFUNDED，本单发放的权益被回收，流水记退款额")
    void refundRevokesTheEntitlementGrantedByThatOrder() {
        MallOrder order = orderService.create(buyer.getId(), WORD_BOOK);
        orderService.markPaid(order, confirm(order, "T-0400", null));

        assertThat(orderService.markRefunded(order, new BigDecimal("6.66"))).isTrue();
        // 重复退款同样是 no-op
        assertThat(orderService.markRefunded(order, new BigDecimal("6.66"))).isFalse();

        MallOrder refunded = orderMapper.selectById(order.getId());
        assertThat(refunded.getStatus()).isEqualTo(OrderStatus.REFUNDED);
        assertThat(refunded.getRefundedTime()).isNotNull();

        MallEntitlement entitlement = entitlementService.find(buyer.getId(), WORD_BOOK);
        assertThat(entitlement.getStatus()).isEqualTo(EntitlementStatus.REVOKED);
        assertThat(entitlement.getRevokedTime()).isNotNull();

        MallPayment payment = orderService.findPayment(order.getOrderNo());
        assertThat(payment.getRefundAmount()).isEqualByComparingTo("6.66");
        assertThat(payment.getTradeStatus()).isEqualTo(AlipayTradeStatus.TRADE_CLOSED);
    }

    @Test
    @DisplayName("回收只针对本单：另一笔订单发的权益不受退款影响")
    void refundLeavesEntitlementsFromOtherOrdersAlone() {
        MallOrder first = orderService.create(buyer.getId(), WORD_BOOK);
        MallOrder second = orderService.create(buyer.getId(), ANOTHER_PRODUCT);
        orderService.markPaid(first, confirm(first, "T-0500", null));
        orderService.markPaid(second, confirm(second, "T-0501", null));

        orderService.markRefunded(first, new BigDecimal("6.66"));

        assertThat(entitlementService.find(buyer.getId(), WORD_BOOK).getStatus())
                .isEqualTo(EntitlementStatus.REVOKED);
        assertThat(entitlementService.find(buyer.getId(), ANOTHER_PRODUCT).getStatus())
                .isEqualTo(EntitlementStatus.ACTIVE);
    }

    @Test
    @DisplayName("已持有 ACTIVE 权益禁止重复下单；退款后可重购，upsert 复用同一行")
    void entitlementUniqueKeyReusesTheSameRowOnRepurchase() {
        MallOrder first = orderService.create(buyer.getId(), WORD_BOOK);
        orderService.markPaid(first, confirm(first, "T-0600", null));

        assertThatThrownBy(() -> orderService.create(buyer.getId(), WORD_BOOK))
                .isInstanceOf(BizException.class)
                .hasMessage("已购买过该商品，无需重复下单");

        orderService.markRefunded(first, new BigDecimal("6.66"));
        long rowId = entitlementService.find(buyer.getId(), WORD_BOOK).getId();

        MallOrder second = orderService.create(buyer.getId(), WORD_BOOK);
        orderService.markPaid(second, confirm(second, "T-0601", null));

        MallEntitlement now = entitlementService.find(buyer.getId(), WORD_BOOK);
        assertThat(now.getId()).isEqualTo(rowId);
        assertThat(now.getStatus()).isEqualTo(EntitlementStatus.ACTIVE);
        assertThat(now.getRevokedTime()).isNull();
        assertThat(now.getOrderId()).isEqualTo(second.getId());
        assertThat(entitlementMapper.selectCount(Wrappers.<MallEntitlement>lambdaQuery()
                .eq(MallEntitlement::getUserId, buyer.getId())
                .eq(MallEntitlement::getProductId, WORD_BOOK))).isEqualTo(1L);
    }

    @Test
    @DisplayName("uk_pay_trade_no：一笔支付宝交易号只能给一个订单入账")
    void tradeNoCannotPayTwoOrders() {
        MallOrder first = orderService.create(buyer.getId(), WORD_BOOK);
        MallOrder second = orderService.create(buyer.getId(), ANOTHER_PRODUCT);

        // markPaid 里的 insertPaymentOrRejectDuplicateTrade 依赖的就是这条唯一索引，
        // 它把 DuplicateKeyException 翻译成「交易号已关联其它订单」的业务错误
        MallPayment a = pendingPayment(first);
        a.setTradeNo("T-0700");
        paymentMapper.insert(a);

        MallPayment b = pendingPayment(second);
        b.setTradeNo("T-0700");
        assertThatThrownBy(() -> paymentMapper.insert(b))
                .isInstanceOf(DuplicateKeyException.class);

        assertThat(paymentMapper.selectCount(Wrappers.<MallPayment>lambdaQuery()
                .eq(MallPayment::getTradeNo, "T-0700"))).isEqualTo(1L);
    }

    @Test
    @DisplayName("中文与枚举全程无损：订单快照、已购列表里的商品名不是 ????")
    void chineseSurvivesTheWholeRoundTrip() {
        MallOrder order = orderService.create(buyer.getId(), WORD_BOOK);
        assertThat(order.getProductName()).isEqualTo("考研核心词汇");

        orderService.markPaid(order, confirm(order, "T-0800", null));

        assertThat(entitlementService.find(buyer.getId(), WORD_BOOK).getCategory())
                .isEqualTo(ProductCategory.WORD_BOOK);
        assertThat(entitlementService.listMine(buyer.getId()))
                .singleElement()
                .satisfies(vo -> {
                    assertThat(vo.productName()).isEqualTo("考研核心词汇");
                    assertThat(vo.categoryLabel()).isEqualTo("单词本");
                    assertThat(vo.status()).isEqualTo("ACTIVE");
                });
    }

    private MallPayment pendingPayment(MallOrder order) {
        MallPayment payment = new MallPayment();
        payment.setOrderId(order.getId());
        payment.setOutTradeNo(order.getOrderNo());
        payment.setChannel("ALIPAY");
        payment.setTradeStatus(AlipayTradeStatus.WAIT_BUYER_PAY);
        payment.setTotalAmount(order.getPayAmount());
        payment.setRefundAmount(BigDecimal.ZERO);
        return payment;
    }

    private TradeConfirm confirm(MallOrder order, String tradeNo, LocalDateTime gmtPayment) {
        return TradeConfirm.fromQuery(order.getOrderNo(), tradeNo, AlipayTradeStatus.TRADE_SUCCESS,
                "2088301234567890", OrderService.toAlipayAmount(order.getPayAmount()), gmtPayment);
    }
}
