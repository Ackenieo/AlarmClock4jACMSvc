package org.example.acm.mall.config;

import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

@Configuration
public class AlipayClientConfig {

    /**
     * @Lazy 是保险：DefaultAlipayClient 的构造函数只是存字段、不解析密钥，占位值不会阻塞启动，
     * 失败点在 pageExecute 签名时才出现。真正解析密钥留给第一次调用。
     */
    @Bean
    @Lazy
    public AlipayClient alipayClient(AlipayProperties properties) {
        return new DefaultAlipayClient(
                properties.getGateway(),
                properties.getAppId(),
                properties.getPrivateKey(),
                properties.getFormat(),
                properties.getCharset(),
                properties.getAlipayPublicKey(),
                properties.getSignType());
    }
}
