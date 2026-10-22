package org.example.acm.mall.service;

import org.example.acm.mall.config.AppProperties;
import org.example.acm.mall.entity.MallOrder;
import org.example.acm.mall.vo.PaySyncVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 超时订单兜底。默认关闭（app.order.auto-close-enabled=false），避免本地演示时定时任务抢先改状态。
 * 处理顺序是先查单再决定：买家刚好在过期瞬间付了款，直接关单会让钱货两空。
 */
@Service
public class OrderTimeoutService {

    private static final Logger log = LoggerFactory.getLogger(OrderTimeoutService.class);
    private static final int BATCH_SIZE = 50;

    private final AppProperties appProperties;
    private final OrderService orderService;
    private final PaymentService paymentService;
    private final AlipayGatewayService gateway;

    public OrderTimeoutService(AppProperties appProperties,
                               OrderService orderService,
                               PaymentService paymentService,
                               AlipayGatewayService gateway) {
        this.appProperties = appProperties;
        this.orderService = orderService;
        this.paymentService = paymentService;
        this.gateway = gateway;
    }

    @Scheduled(fixedDelayString = "PT5M")
    public void closeExpiredOrders() {
        if (!appProperties.getOrder().isAutoCloseEnabled() || !gateway.isReady()) {
            return;
        }
        List<MallOrder> expired = orderService.findExpiredWaitingPay(BATCH_SIZE);
        for (MallOrder order : expired) {
            try {
                PaySyncVO sync = paymentService.sync(order);
                if (sync.changed()) {
                    log.info("订单 {} 过期但已付款，按已支付处理", order.getOrderNo());
                    continue;
                }
                orderService.markClosed(order);
                log.info("订单 {} 超时关闭", order.getOrderNo());
            } catch (RuntimeException ex) {
                // 单笔失败不能阻断整批，支付宝侧抖动时下一轮还会再试
                log.warn("处理超时订单 {} 失败：{}", order.getOrderNo(), ex.getMessage());
            }
        }
    }
}
