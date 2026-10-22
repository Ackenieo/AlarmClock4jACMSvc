package org.example.acm.mall.security;

/**
 * 从令牌解析出的登录态。用请求属性传递而非 ThreadLocal，避免线程池复用时串号。
 */
public record LoginUser(Long id, String email, String nickname) {
}
