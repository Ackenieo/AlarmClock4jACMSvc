package org.example.acm.mall.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.example.acm.mall.service.AlipayGatewayService;
import org.example.acm.mall.service.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 支付宝异步通知。本地 localhost 收不到（这是服务器对服务器的 POST），
 * 需要内网穿透并把公网地址配到 alipay.notify-url 才会真正被调用。
 */
@Tag(name = "05-支付宝回调", description = "验签与幂等入账，无需登录。需要公网可达地址才会被支付宝调用")
@RestController
@RequestMapping("/api/alipay")
public class AlipayNotifyController {

    private static final Logger log = LoggerFactory.getLogger(AlipayNotifyController.class);

    /** 支付宝要求收到字面量 success 才停止重试；回其它内容会按约定间隔重发 */
    private static final String SUCCESS = "success";
    private static final String FAILURE = "failure";

    private final AlipayGatewayService gateway;
    private final PaymentService paymentService;

    public AlipayNotifyController(AlipayGatewayService gateway, PaymentService paymentService) {
        this.gateway = gateway;
        this.paymentService = paymentService;
    }

    @Operation(summary = "异步通知", description = """
            返回纯文本 success / failure，不是 JSON —— 因此本项目刻意没有全局 ResponseBodyAdvice，
            任何统一包装都会让支付宝收不到预期值而无限重试。

            伪造签名必须被拒绝：可以用 curl 发一个 sign=bogus 的请求验证返回 failure。""")
    @PostMapping(value = "/notify",
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = MediaType.TEXT_PLAIN_VALUE)
    public String notify(HttpServletRequest request) {
        // 必须传副本：SDK 的 rsaCheckV1 会 remove 掉传入 Map 里的 sign 与 sign_type，
        // 直接用原始 Map 会让日志与后续处理拿不到签名
        Map<String, String> params = copyParams(request);
        if (!gateway.isReady()) {
            log.warn("收到支付宝通知但凭证未配置，丢弃。out_trade_no={}", params.get("out_trade_no"));
            return FAILURE;
        }
        if (!gateway.verifyNotifySign(new HashMap<>(params))) {
            log.error("异步通知验签失败，疑似伪造。out_trade_no={}", params.get("out_trade_no"));
            return FAILURE;
        }
        try {
            boolean handled = paymentService.handleNotify(params);
            // handled=false 可能是重复通知已被处理过，也可能是订单/金额校验未通过，
            // 只有后者需要支付宝重试，这里统一回 success 避免无意义的重试风暴
            log.info("异步通知处理完成 out_trade_no={} 状态变更={}", params.get("out_trade_no"), handled);
            return SUCCESS;
        } catch (RuntimeException ex) {
            log.error("异步通知处理异常，返回 failure 让支付宝重试。out_trade_no={}",
                    params.get("out_trade_no"), ex);
            return FAILURE;
        }
    }

    private static Map<String, String> copyParams(HttpServletRequest request) {
        Map<String, String> params = new HashMap<>();
        request.getParameterMap().forEach((key, values) -> {
            if (values != null && values.length > 0) {
                params.put(key, values[0]);
            }
        });
        return params;
    }
}
