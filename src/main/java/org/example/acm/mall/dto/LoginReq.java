package org.example.acm.mall.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "登录请求")
public record LoginReq(

        @Schema(description = "登录邮箱", example = "demo@test.com")
        @NotBlank(message = "邮箱不能为空")
        String email,

        @Schema(description = "密码", example = "Passw0rd")
        @NotBlank(message = "密码不能为空")
        String password) {
}
