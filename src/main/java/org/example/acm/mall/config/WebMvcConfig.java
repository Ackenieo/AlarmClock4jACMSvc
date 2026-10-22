package org.example.acm.mall.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.acm.mall.security.CurrentUserArgumentResolver;
import org.example.acm.mall.security.JwtAuthInterceptor;
import org.example.acm.mall.security.JwtTokenService;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final JwtTokenService tokenService;
    private final ObjectMapper objectMapper;

    public WebMvcConfig(JwtTokenService tokenService, ObjectMapper objectMapper) {
        this.tokenService = tokenService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new JwtAuthInterceptor(tokenService, objectMapper))
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/auth/**",
                        "/api/products",
                        "/api/products/**",
                        "/api/ping",
                        // 支付宝服务器对服务器的异步通知，靠验签保护而非登录态
                        "/api/alipay/notify",
                        // 浏览器支付完跳转回来的落地页，无签名，仅展示
                        "/api/pay/return",
                        // 必须放行：Boot 把异常 forward 到 /error，再过一次拦截器会让参数校验
                        // 错误被 401 盖掉，真正的错误信息看不到
                        "/error");
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new CurrentUserArgumentResolver());
    }
}
