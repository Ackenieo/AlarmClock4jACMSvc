package org.example.acm.mall.service;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.AlipayResponse;
import com.alipay.api.domain.AlipayTradeCloseModel;
import com.alipay.api.domain.AlipayTradePagePayModel;
import com.alipay.api.domain.AlipayTradeQueryModel;
import com.alipay.api.domain.AlipayTradeRefundModel;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradeCloseRequest;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.request.AlipayTradeRefundRequest;
import com.alipay.api.response.AlipayTradeCloseResponse;
import com.alipay.api.response.AlipayTradePagePayResponse;
import com.alipay.api.response.AlipayTradeQueryResponse;
import com.alipay.api.response.AlipayTradeRefundResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.acm.mall.common.BizException;
import org.example.acm.mall.common.ResultCode;
import org.example.acm.mall.config.AlipayProperties;
import org.example.acm.mall.entity.MallOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * 支付宝 SDK 的薄封装：组装请求、转译异常，不做业务判断。
 */
@Service
public class AlipayGatewayService {

    private static final Logger log = LoggerFactory.getLogger(AlipayGatewayService.class);

    /** 电脑网站支付固定值，其它取值会被沙箱拒绝 */
    private static final String PRODUCT_CODE = "FAST_INSTANT_TRADE_PAY";
    private static final DateTimeFormatter ALIPAY_DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ObjectProvider<AlipayClient> clientProvider;
    private final AlipayProperties properties;
    private final ObjectMapper objectMapper;

    public AlipayGatewayService(ObjectProvider<AlipayClient> clientProvider,
                                AlipayProperties properties,
                                ObjectMapper objectMapper) {
        this.clientProvider = clientProvider;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public boolean isReady() {
        return properties.isConfigured();
    }

    public AlipayProperties properties() {
        return properties;
    }

    /**
     * @param httpMethod GET 返回已签名的完整网关 URL；POST 返回自动提交的表单 HTML
     */
    public String pagePay(MallOrder order, String httpMethod) {
        AlipayTradePagePayRequest request = new AlipayTradePagePayRequest();
        request.setNotifyUrl(properties.getNotifyUrl());
        request.setReturnUrl(properties.getReturnUrl() + "?out_trade_no=" + order.getOrderNo());

        AlipayTradePagePayModel model = new AlipayTradePagePayModel();
        model.setOutTradeNo(order.getOrderNo());
        model.setTotalAmount(OrderService.toAlipayAmount(order.getPayAmount()));
        model.setSubject(properties.getSubjectPrefix() + order.getProductName());
        model.setBody(order.getProductName());
        model.setProductCode(PRODUCT_CODE);
        // 用绝对时间而不是 timeout_express 相对值，保证支付宝关单点与本地 expire_time 完全一致
        model.setTimeExpire(order.getExpireTime().format(ALIPAY_DATETIME));
        request.setBizModel(model);

        AlipayTradePagePayResponse response = execute(client -> client.pageExecute(request, httpMethod));
        if (!response.isSuccess()) {
            throw BizException.of(ResultCode.ALIPAY_ERROR,
                    "下单到支付宝失败：" + response.getSubCode() + " " + response.getSubMsg());
        }
        // GET 时 body 是 https 跳转链接，POST 时是自动提交的 <form> HTML
        return response.getBody();
    }

    public AlipayTradeQueryResponse query(String outTradeNo) {
        AlipayTradeQueryRequest request = new AlipayTradeQueryRequest();
        AlipayTradeQueryModel model = new AlipayTradeQueryModel();
        model.setOutTradeNo(outTradeNo);
        request.setBizModel(model);
        return execute(client -> client.execute(request));
    }

    public AlipayTradeRefundResponse refund(String outTradeNo, String outRequestNo,
                                            String refundAmount, String reason) {
        AlipayTradeRefundRequest request = new AlipayTradeRefundRequest();
        AlipayTradeRefundModel model = new AlipayTradeRefundModel();
        model.setOutTradeNo(outTradeNo);
        model.setOutRequestNo(outRequestNo);
        model.setRefundAmount(refundAmount);
        model.setRefundReason(reason);
        request.setBizModel(model);
        return execute(client -> client.execute(request));
    }

    public AlipayTradeCloseResponse close(String outTradeNo) {
        AlipayTradeCloseRequest request = new AlipayTradeCloseRequest();
        AlipayTradeCloseModel model = new AlipayTradeCloseModel();
        model.setOutTradeNo(outTradeNo);
        request.setBizModel(model);
        return execute(client -> client.execute(request));
    }

    /**
     * 异步通知验签。刻意用 rsaCheckV1 而非 V2：支付宝对除 sign 与 sign_type 之外的全部通知参数签名，
     * V1 正好剔除这两个键、按 key 升序拼接并跳过空值；V2 只剔 sign，用它验签必然失败。
     * SDK 会修改传入的 Map，调用方必须给副本，否则日志与重试时拿不到 sign。
     */
    public boolean verifyNotifySign(Map<String, String> paramsCopy) {
        try {
            return AlipaySignature.rsaCheckV1(paramsCopy, properties.getAlipayPublicKey(),
                    properties.getCharset(), properties.getSignType());
        } catch (AlipayApiException ex) {
            log.warn("异步通知验签异常：{}", ex.getErrMsg());
            return false;
        }
    }

    public String toJson(Map<String, String> params) {
        try {
            return objectMapper.writeValueAsString(params);
        } catch (JsonProcessingException ex) {
            return String.valueOf(params);
        }
    }

    /**
     * AlipayApiException 与 RuntimeException 都要接住：SDK 内部 DefaultSigner 会把前者重新包装成
     * RuntimeException 抛出，只 catch 受检异常会让「密钥没配好」变成 500 堆栈。
     */
    private <R extends AlipayResponse> R execute(ClientCall<R> call) {
        if (!isReady()) {
            throw new BizException(ResultCode.ALIPAY_ERROR,
                    "支付宝沙箱凭证未配置：请在 application.yml 填入 alipay.app-id / private-key / alipay-public-key 并把 alipay.enabled 设为 true");
        }
        try {
            return call.apply(clientProvider.getObject());
        } catch (AlipayApiException | RuntimeException ex) {
            log.error("调用支付宝接口失败", ex);
            throw new BizException(ResultCode.ALIPAY_ERROR, "调用支付宝接口失败：" + rootMessage(ex));
        }
    }

    private static String rootMessage(Throwable ex) {
        Throwable cursor = ex;
        while (cursor.getCause() != null && cursor.getCause() != cursor) {
            cursor = cursor.getCause();
        }
        String message = cursor.getMessage();
        return message == null || message.isBlank() ? cursor.getClass().getSimpleName() : message;
    }

    @FunctionalInterface
    private interface ClientCall<R extends AlipayResponse> {
        R apply(AlipayClient client) throws AlipayApiException;
    }
}
