package org.example.acm.mall.vo;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "登录用户")
public record UserVO(
        Long id,
        String email,
        String nickname) {
}
