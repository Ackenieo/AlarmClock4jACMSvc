package org.example.acm.mall.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "注册请求")
public record RegisterReq(

        @Schema(description = "登录邮箱", example = "demo@test.com")
        @NotBlank(message = "邮箱不能为空")
        @Email(message = "邮箱格式不正确")
        @Size(max = 128, message = "邮箱过长")
        String email,

        @Schema(description = "密码：8-32 位，需同时包含字母和数字", example = "Passw0rd")
        @NotBlank(message = "密码不能为空")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)[^\\s]{8,32}$", message = "密码需 8-32 位且同时包含字母和数字")
        String password,

        @Schema(description = "昵称", example = "demo")
        @NotBlank(message = "昵称不能为空")
        @Size(max = 64, message = "昵称过长")
        String nickname) {
}
