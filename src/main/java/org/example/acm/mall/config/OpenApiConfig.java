package org.example.acm.mall.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    public static final String BEARER_SCHEME = "Bearer Token";

    @Bean
    public OpenAPI mallOpenAPI() {
        // 只注册方案，不做全局要求：公开接口（登录、商品、回调）因此不会显示挂锁。
        // 需要鉴权的 Controller 各自标 @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
        return new OpenAPI()
                .info(new Info()
                        .title("闹钟 App 虚拟商品商城 API")
                        .version("1.0.0")
                        .description("""
                                售卖单词本与应用皮肤，支付走支付宝沙箱。

                                鉴权：先调 /api/auth/login 拿 accessToken，点右上 Authorize 填入即可调用带锁接口。

                                支付说明：本地 localhost 收不到支付宝异步通知，付款完成后请调
                                POST /api/orders/{id}/sync 主动查单来确认支付结果。
                                """))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .name(BEARER_SCHEME)));
    }
}
