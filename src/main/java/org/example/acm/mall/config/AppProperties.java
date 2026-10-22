package org.example.acm.mall.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * app.* 业务开关。字段带默认值，配置缺失时也能跑。
 */
@Data
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private Order order = new Order();

    @Data
    public static class Order {

        /** 订单有效期（分钟），同时用于支付宝 timeout_express */
        private int expireMinutes = 30;

        /** 本地演示默认关掉自动关单，避免定时任务抢先改状态 */
        private boolean autoCloseEnabled = false;

        /** GET 订单详情时是否顺带向支付宝查单（默认关，GET 不发网络请求） */
        private boolean syncOnRead = false;
    }
}
