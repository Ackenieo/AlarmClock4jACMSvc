package org.example.acm.mall.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "alipay")
public class AlipayProperties {

    /** 凭证是否已填好。false 时支付类接口返回可读的业务错误而不是堆栈 */
    private boolean enabled = false;

    private String gateway;

    private String appId;

    /** PKCS#8、单行、无 PEM 头尾 */
    private String privateKey;

    /** 沙箱控制台里的「支付宝公钥」，不是应用公钥 */
    private String alipayPublicKey;

    private String signType = "RSA2";

    private String format = "json";

    private String charset = "UTF-8";

    private String subjectPrefix = "闹钟商城-";

    /** 服务器对服务器的异步通知地址，localhost 收不到，需内网穿透 */
    private String notifyUrl;

    /** 浏览器 302 跳转地址，本地可达，但参数无签名，仅用于展示 */
    private String returnUrl;

    /** 占位值未替换时视为未配置，避免用户忘了改 enabled 就一直对着堆栈猜 */
    public boolean isConfigured() {
        return enabled
                && isReal(appId) && !appId.contains("PLACEHOLDER")
                && isReal(privateKey) && !privateKey.startsWith("REPLACE_WITH")
                && isReal(alipayPublicKey) && !alipayPublicKey.startsWith("REPLACE_WITH");
    }

    private static boolean isReal(String value) {
        return value != null && !value.isBlank();
    }
}
