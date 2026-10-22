package org.example.acm.mall.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    /** HS256 要求至少 32 字节 */
    private String secret;

    private long expireMinutes = 120;

    private String issuer = "acm-mall";
}
