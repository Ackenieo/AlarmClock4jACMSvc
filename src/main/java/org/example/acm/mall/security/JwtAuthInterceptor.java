package org.example.acm.mall.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.acm.mall.common.BizException;
import org.example.acm.mall.common.Result;
import org.example.acm.mall.common.ResultCode;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * JWT 校验。刻意不用 Spring Security：Boot 3 的 Security 6 默认开 CSRF，会把支付宝异步通知的
 * 表单 POST 直接 403 拦掉，容易被误判成签名问题；路径白名单写漏还会让 Swagger UI 打不开。
 */
public class JwtAuthInterceptor implements HandlerInterceptor {

    public static final String ATTRIBUTE = "acm.mall.loginUser";

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenService tokenService;
    private final ObjectMapper objectMapper;

    public JwtAuthInterceptor(JwtTokenService tokenService, ObjectMapper objectMapper) {
        this.tokenService = tokenService;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        LoginUser user = tokenService.parse(readBearerToken(request));
        if (user == null) {
            // 不在这里抛异常：preHandle 抛出的异常会走 /error forward，错误信息容易被盖掉
            reject(response);
            return false;
        }
        request.setAttribute(ATTRIBUTE, user);
        return true;
    }

    private String readBearerToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return null;
        }
        return header.substring(BEARER_PREFIX.length()).trim();
    }

    private void reject(HttpServletResponse response) throws Exception {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), Result.fail(ResultCode.UNAUTHORIZED));
    }

    public static LoginUser require(HttpServletRequest request) {
        Object attribute = request.getAttribute(ATTRIBUTE);
        if (attribute == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        return (LoginUser) attribute;
    }
}
