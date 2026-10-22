package org.example.acm.mall.service;

import com.alipay.api.response.AlipayTradeQueryResponse;
import com.alipay.api.response.AlipayTradeRefundResponse;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.example.acm.mall.common.BizException;
import org.example.acm.mall.common.ResultCode;
import org.example.acm.mall.entity.MallOrder;
import org.example.acm.mall.entity.MallPayment;
import org.example.acm.mall.enums.AlipayTradeStatus;
import org.example.acm.mall.enums.OrderStatus;
import org.example.acm.mall.enums.RefundStatus;
import org.example.acm.mall.mapper.MallRefundMapper;
import org.example.acm.mall.entity.MallRefund;
import org.example.acm.mall.vo.PayInitVO;
import org.example.acm.mall.vo.PaySyncVO;
import org.example.acm.mall.vo.RefundVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.Map;

/**
 * 支付编排：调 SDK 一律在事务之外，落库交给 OrderService 的短事务。
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    /** 支付宝侧交易不存在的 subCode，表示「还没付」，不是错误 */
    private static final String TRADE_NOT_EXIST = "ACQ.TRADE_NOT_EXIST";

    private static final String TIP_GET = "把 payUrl 复制到浏览器打开沙箱收银台完成付款，"
            + "然后调 POST /api/orders/{id}/sync 确认支付结果（本地收不到异步通知）";

    private final AlipayGatewayService gateway;
    private final OrderService orderService;
    private final MallRefundMapper refundMapper;

    public PaymentService(AlipayGatewayService gateway, OrderService orderService, MallRefundMapper refundMapper) {
        this.gateway = gateway;
        this.orderService = orderService;
        this.refundMapper = refundMapper;
    }

    public PayInitVO initPay(MallOrder order, String httpMethod) {
        if (order.getStatus() != OrderStatus.WAITING_PAY) {
            throw new BizException(ResultCode.CONFLICT, "订单当前状态不可支付：" + order.getStatus().getLabel());
        }
        if (order.getExpireTime().isBefore(LocalDateTime.now())) {
            throw new BizException(ResultCode.CONFLICT, "订单已超时，请重新下单");
        }
        String payload = gateway.pagePay(order, httpMethod);
        boolean get = "GET".equalsIgnoreCase(httpMethod);
        recordPaymentAttempt(order);
        return new PayInitVO(order.getOrderNo(), order.getPayAmount(), get ? "GET" : "POST",
                get ? payload : null, get ? null : payload, get ? TIP_GET : "表单 HTML，需另存为 .html 文件用浏览器打开");
    }

    /** 下单成功但没落流水的话，后面查单/对账就没有锚点，所以这里先建一条未支付记录 */
    private void recordPaymentAttempt(MallOrder order) {
        if (orderService.findPayment(order.getOrderNo()) != null) {
            return;
        }
        MallPayment payment = new MallPayment();
        payment.setOrderId(order.getId());
        payment.setOutTradeNo(order.getOrderNo());
        payment.setChannel("ALIPAY");
        payment.setTotalAmount(order.getPayAmount());
        payment.setRefundAmount(BigDecimal.ZERO);
        payment.setLastSyncTime(LocalDateTime.now());
        orderService.upsertPayment(payment);
    }

    /**
     * 主动查单。这是本地演示确认支付的主路径：异步通知需要公网地址才能收到。
     */
    public PaySyncVO sync(MallOrder order) {
        if (order.getStatus() != OrderStatus.WAITING_PAY) {
            return new PaySyncVO(order.getOrderNo(), null, null, order.getStatus().name(), false,
                    order.getPaidTime(), "订单已是「" + order.getStatus().getLabel() + "」，无需同步");
        }
        AlipayTradeQueryResponse response = gateway.query(order.getOrderNo());

        if (!response.isSuccess()) {
            if (TRADE_NOT_EXIST.equals(response.getSubCode())) {
                return new PaySyncVO(order.getOrderNo(), null, AlipayTradeStatus.WAIT_BUYER_PAY,
                        order.getStatus().name(), false, null, "支付宝侧尚无此交易，买家还未付款");
            }
            throw BizException.of(ResultCode.ALIPAY_ERROR,
                    "查单失败：" + response.getSubCode() + " " + response.getSubMsg());
        }

        // 报文金额与订单金额必须完全一致，否则可能是张冠李戴的交易
        BigDecimal reported = new BigDecimal(response.getTotalAmount());
        if (reported.compareTo(order.getPayAmount()) != 0) {
            log.error("订单 {} 金额不一致：支付宝 {} 本地 {}", order.getOrderNo(), reported, order.getPayAmount());
            throw new BizException(ResultCode.BIZ_ERROR, "支付宝回传金额与订单金额不一致，已拒绝入账");
        }

        String tradeStatus = response.getTradeStatus();
        if (!AlipayTradeStatus.isPaid(tradeStatus)) {
            return new PaySyncVO(order.getOrderNo(), response.getTradeNo(), tradeStatus,
                    order.getStatus().name(), false, null, "交易状态为 " + tradeStatus + "，尚未支付完成");
        }

        TradeConfirm confirm = TradeConfirm.fromQuery(order.getOrderNo(), response.getTradeNo(), tradeStatus,
                response.getBuyerUserId(), response.getTotalAmount(), toLocalDateTime(response.getSendPayDate()));
        boolean changed = orderService.markPaid(order, confirm);
        return new PaySyncVO(order.getOrderNo(), response.getTradeNo(), tradeStatus,
                changed ? OrderStatus.PAID.name() : order.getStatus().name(), changed,
                changed ? confirm.gmtPayment() : order.getPaidTime(),
                changed ? "支付确认完成，商品已解锁" : "订单已被其它途径确认，本次为幂等空操作");
    }

    /**
     * 异步通知处理。返回 false 表示处理失败，Controller 需回 failure 让支付宝重试。
     */
    public boolean handleNotify(Map<String, String> params) {
        String outTradeNo = params.get("out_trade_no");
        String tradeStatus = params.get("trade_status");
        if (outTradeNo == null || outTradeNo.isBlank()) {
            log.warn("通知缺少 out_trade_no，忽略");
            return false;
        }
        MallOrder order;
        try {
            order = orderService.findByOrderNo(outTradeNo);
        } catch (BizException ex) {
            log.warn("通知对应的订单不存在：{}", outTradeNo);
            return false;
        }

        // 验签通过仍要校验业务字段：签名只证明来自支付宝，不证明金额与交易归属正确
        if (!gateway.properties().getAppId().equals(params.get("app_id"))) {
            log.error("通知 app_id 不匹配，拒绝：{}", params.get("app_id"));
            return false;
        }
        BigDecimal reported = new BigDecimal(params.get("total_amount"));
        if (reported.compareTo(order.getPayAmount()) != 0) {
            log.error("通知金额与订单不一致，拒绝。订单 {} 本地 {} 通知 {}",
                    outTradeNo, order.getPayAmount(), reported);
            return false;
        }
        if (!AlipayTradeStatus.isPaid(tradeStatus)) {
            log.info("通知状态 {} 不是已支付，订单 {} 不改状态", tradeStatus, outTradeNo);
            return true;
        }

        TradeConfirm confirm = new TradeConfirm(outTradeNo, params.get("trade_no"), tradeStatus,
                params.get("buyer_user_id"), params.get("total_amount"),
                parseNotifyTime(params.get("gmt_payment")), gateway.toJson(params));
        // markPaid 内部是条件更新，支付宝重复通知（它确实会重发）只会有一次生效
        return orderService.markPaid(order, confirm);
    }

    public RefundVO refund(MallOrder order, String reason) {
        if (order.getStatus() != OrderStatus.PAID) {
            throw new BizException(ResultCode.CONFLICT, "只有已支付订单可退款，当前：" + order.getStatus().getLabel());
        }
        MallPayment payment = orderService.findPayment(order.getOrderNo());
        if (payment == null || payment.getTradeNo() == null) {
            throw new BizException(ResultCode.BIZ_ERROR, "缺少支付宝交易号，无法退款");
        }
        String amount = OrderService.toAlipayAmount(order.getPayAmount());
        // out_request_no 固定由订单号派生：重复点击或支付宝重试都落在同一次退款上
        String outRequestNo = "RF" + order.getOrderNo();

        AlipayTradeRefundResponse response = gateway.refund(order.getOrderNo(), outRequestNo, amount, reason);
        if (!response.isSuccess()) {
            saveRefund(order, outRequestNo, amount, reason, RefundStatus.FAIL, response.getSubMsg(), null);
            throw new BizException(ResultCode.ALIPAY_ERROR,
                    "退款失败：" + response.getSubCode() + " " + response.getSubMsg());
        }
        boolean fundChanged = "Y".equalsIgnoreCase(response.getFundChange());
        LocalDateTime refundTime = toLocalDateTime(response.getGmtRefundPay());
        saveRefund(order, outRequestNo, amount, reason, RefundStatus.SUCCESS, response.getBody(), refundTime);
        boolean transitioned = orderService.markRefunded(order, new BigDecimal(amount));
        log.info("订单 {} 退款受理成功，资金变动={}，状态迁移={}", order.getOrderNo(), fundChanged, transitioned);
        return new RefundVO(order.getOrderNo(), outRequestNo, new BigDecimal(response.getRefundFee()),
                RefundStatus.SUCCESS.name(), fundChanged, refundTime);
    }

    private void saveRefund(MallOrder order, String outRequestNo, String amount, String reason,
                            RefundStatus status, String raw, LocalDateTime refundTime) {
        // uk_refund_out_request_no 保证同一订单只有一行退款记录：重试时更新而不是插入
        MallRefund refund = refundMapper.selectOne(Wrappers.<MallRefund>lambdaQuery()
                .eq(MallRefund::getOutRequestNo, outRequestNo));
        boolean isNew = refund == null;
        if (isNew) {
            refund = new MallRefund();
            refund.setOrderId(order.getId());
            refund.setOutTradeNo(order.getOrderNo());
            refund.setOutRequestNo(outRequestNo);
        }
        refund.setRefundAmount(new BigDecimal(amount));
        refund.setReason(reason);
        refund.setStatus(status);
        refund.setFundChange(status == RefundStatus.SUCCESS && refundTime != null ? 1 : 0);
        refund.setGmtRefund(refundTime);
        refund.setRawResponse(truncate(raw));
        if (isNew) {
            refundMapper.insert(refund);
        } else {
            refundMapper.updateById(refund);
        }
    }

    public boolean close(MallOrder order) {
        if (order.getStatus() != OrderStatus.WAITING_PAY) {
            throw new BizException(ResultCode.CONFLICT, "只有待支付订单可关闭，当前：" + order.getStatus().getLabel());
        }
        if (gateway.isReady()) {
            var response = gateway.close(order.getOrderNo());
            // 从未产生过交易时支付宝没有可关的单，本地直接关闭即可
            if (!response.isSuccess() && !TRADE_NOT_EXIST.equals(response.getSubCode())) {
                throw new BizException(ResultCode.ALIPAY_ERROR,
                        "关闭交易失败：" + response.getSubCode() + " " + response.getSubMsg());
            }
        }
        return orderService.markClosed(order);
    }

    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 1000 ? value : value.substring(0, 1000);
    }

    private static LocalDateTime toLocalDateTime(Date date) {
        return date == null ? LocalDateTime.now() : LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
    }

    /** 通知里的 gmt_payment 是 yyyy-MM-dd HH:mm:ss 字符串 */
    private static LocalDateTime parseNotifyTime(String value) {
        if (value == null || value.isBlank()) {
            return LocalDateTime.now();
        }
        try {
            return LocalDateTime.parse(value, java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } catch (RuntimeException ex) {
            return LocalDateTime.now();
        }
    }
}
