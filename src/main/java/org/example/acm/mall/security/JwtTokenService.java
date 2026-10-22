package org.example.acm.mall.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.example.acm.mall.config.JwtProperties;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
public class JwtTokenService {

    private final JwtProperties properties;
    private final SecretKey key;

    public JwtTokenService(JwtProperties properties) {
        this.properties = properties;
        // HS256 的密钥不足 32 字节时这里直接抛 WeakKeyException，启动阶段就暴露配置问题
        this.key = Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String generate(LoginUser user) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + properties.getExpireMinutes() * 60_000L);
        return Jwts.builder()
                .subject(String.valueOf(user.id()))
                .claim("email", user.email())
                .claim("nickname", user.nickname())
                .issuer(properties.getIssuer())
                .issuedAt(now)
                .expiration(expiration)
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * @return 解析出的登录态；签名错误、过期、issuer 不符时返回 null
     */
    public LoginUser parse(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(properties.getIssuer())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return new LoginUser(Long.valueOf(claims.getSubject()),
                    claims.get("email", String.class),
                    claims.get("nickname", String.class));
        } catch (Exception ex) {
            return null;
        }
    }

    public long expiresInSeconds() {
        return properties.getExpireMinutes() * 60L;
    }
}
