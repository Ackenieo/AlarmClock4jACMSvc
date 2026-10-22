package org.example.acm.mall.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.example.acm.mall.common.PageResult;
import org.example.acm.mall.common.Result;
import org.example.acm.mall.common.ResultCode;
import org.example.acm.mall.config.AppProperties;
import org.example.acm.mall.config.OpenApiConfig;
import org.example.acm.mall.dto.CreateOrderReq;
import org.example.acm.mall.dto.RefundReq;
import org.example.acm.mall.entity.MallOrder;
import org.example.acm.mall.enums.OrderStatus;
import org.example.acm.mall.security.CurrentUser;
import org.example.acm.mall.security.LoginUser;
import org.example.acm.mall.service.OrderService;
import org.example.acm.mall.service.PaymentService;
import org.example.acm.mall.vo.OrderVO;
import org.example.acm.mall.vo.PayInitVO;
import org.example.acm.mall.vo.PaySyncVO;
import org.example.acm.mall.vo.RefundVO;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "03-订单与支付", description = """
        下单、发起支付宝支付、主动查单同步、退款与关闭。全部需要登录。

        演示顺序：POST /api/orders → POST /api/orders/{id}/pay（复制 payUrl 到浏览器付款）
        → POST /api/orders/{id}/sync（确认到账并解锁商品）→ GET /api/me/entitlements 查看已解锁内容。""")
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
@Validated
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;
    private final PaymentService paymentService;
    private final AppProperties appProperties;

    public OrderController(OrderService orderService, PaymentService paymentService, AppProperties appProperties) {
        this.orderService = orderService;
        this.paymentService = paymentService;
        this.appProperties = appProperties;
    }

    @Operation(summary = "下单", description = "一单一个商品，数量固定为 1。已持有有效权益时返回 409")
    @PostMapping
    public Result<OrderVO> create(@CurrentUser LoginUser me, @Valid @RequestBody CreateOrderReq req) {
        MallOrder order = orderService.create(me.id(), req.productId());
        return Result.ok(OrderService.toVO(order));
    }

    @Operation(summary = "我的订单列表", description = "按下单时间倒序")
    @GetMapping
    public Result<PageResult<OrderVO>> page(@CurrentUser LoginUser me,
                                            @RequestParam(defaultValue = "1") @Min(1) long page,
                                            @RequestParam(defaultValue = "10") @Min(1) @Max(100) long size,
                                            @Parameter(description = "WAITING_PAY|PAID|CLOSED|REFUNDED")
                                            @RequestParam(required = false) OrderStatus status) {
        return Result.ok(orderService.pageOfUser(me.id(), page, size, status));
    }

    @Operation(summary = "订单详情",
            description = "默认只读本地数据。把 app.order.sync-on-read 设为 true 才会顺带向支付宝查单")
    @GetMapping("/{id}")
    public Result<OrderVO> detail(@CurrentUser LoginUser me, @PathVariable Long id) {
        MallOrder order = orderService.requireOwned(me.id(), id);
        if (appProperties.getOrder().isSyncOnRead() && order.getStatus() == OrderStatus.WAITING_PAY) {
            paymentService.sync(order);
            order = orderService.requireOwned(me.id(), id);
        }
        return Result.ok(OrderService.toVO(order));
    }

    @Operation(summary = "发起支付", description = """
            mode=GET（默认）返回 payUrl —— 一个已签名的沙箱网关链接，复制到浏览器打开即可进入收银台，
            用沙箱买家账号付款。mode=POST 返回自动提交的表单 HTML，需另存为 .html 打开。

            未配置沙箱凭证时返回业务码 2000 的提示，而不是堆栈。""")
    @PostMapping("/{id}/pay")
    public Result<PayInitVO> pay(@CurrentUser LoginUser me, @PathVariable Long id,
                                 @Parameter(description = "GET|POST") @RequestParam(defaultValue = "GET") String mode) {
        MallOrder order = orderService.requireOwned(me.id(), id);
        return Result.ok(paymentService.initPay(order, mode));
    }

    @Operation(summary = "同步支付状态（主动查单）", description = """
            调 alipay.trade.query 拿支付宝侧的真实结果，成功则把订单从待支付改成已支付并解锁商品。
            本地 localhost 收不到异步通知，这个接口就是确认支付的主路径。
            重复调用是幂等的：第二次的 changed=false 即为证据。""")
    @PostMapping("/{id}/sync")
    public Result<PaySyncVO> sync(@CurrentUser LoginUser me, @PathVariable Long id) {
        MallOrder order = orderService.requireOwned(me.id(), id);
        return Result.ok(paymentService.sync(order));
    }

    @Operation(summary = "退款", description = """
            全额退款。out_request_no 由订单号派生，重复点击落在同一次退款上。
            退款成功后订单转 REFUNDED，本单发放的权益同时被回收。""")
    @PostMapping("/{id}/refund")
    public Result<RefundVO> refund(@CurrentUser LoginUser me, @PathVariable Long id,
                                   @Valid @RequestBody(required = false) RefundReq req) {
        MallOrder order = orderService.requireOwned(me.id(), id);
        String reason = req == null || req.reason() == null ? "用户申请退款" : req.reason();
        return Result.ok(paymentService.refund(order, reason));
    }

    @Operation(summary = "关闭订单", description = "仅待支付订单可关闭。支付宝侧从未产生交易时直接本地关闭")
    @PostMapping("/{id}/close")
    public Result<OrderVO> close(@CurrentUser LoginUser me, @PathVariable Long id) {
        MallOrder order = orderService.requireOwned(me.id(), id);
        boolean closed = paymentService.close(order);
        OrderVO latest = OrderService.toVO(orderService.requireOwned(me.id(), id));
        return closed
                ? Result.ok(latest)
                : Result.fail(ResultCode.CONFLICT, "订单已被其它途径确认，未能关闭");
    }
}
