package org.example.acm.mall.vo;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "登录结果")
public record TokenVO(
        @Schema(description = "访问令牌，后续请求放到 Authorization: Bearer <token>") String accessToken,
        @Schema(description = "令牌类型") String tokenType,
        @Schema(description = "有效期（秒）") long expiresIn,
        UserVO user) {

    public static TokenVO of(String accessToken, long expiresInSeconds, UserVO user) {
        return new TokenVO(accessToken, "Bearer", expiresInSeconds, user);
    }
}
