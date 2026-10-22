package org.example.acm.mall.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.example.acm.mall.common.Result;
import org.example.acm.mall.dto.LoginReq;
import org.example.acm.mall.dto.RegisterReq;
import org.example.acm.mall.entity.MallUser;
import org.example.acm.mall.security.JwtTokenService;
import org.example.acm.mall.security.LoginUser;
import org.example.acm.mall.service.UserService;
import org.example.acm.mall.vo.TokenVO;
import org.example.acm.mall.vo.UserVO;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "01-认证", description = "邮箱密码注册与登录，登录成功返回 JWT")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;
    private final JwtTokenService tokenService;

    public AuthController(UserService userService, JwtTokenService tokenService) {
        this.userService = userService;
        this.tokenService = tokenService;
    }

    @Operation(summary = "注册", description = "邮箱唯一，重复注册返回 409")
    @PostMapping("/register")
    public Result<UserVO> register(@Valid @RequestBody RegisterReq req) {
        return Result.ok(toVO(userService.register(req)));
    }

    @Operation(summary = "登录", description = """
            返回 accessToken。把它填入本页右上 Authorize（或请求头 Authorization: Bearer &lt;token&gt;）
            即可调用带锁接口。账号不存在与密码错误返回同一条提示，避免被枚举。""")
    @PostMapping("/login")
    public Result<TokenVO> login(@Valid @RequestBody LoginReq req) {
        MallUser user = userService.login(req);
        LoginUser loginUser = new LoginUser(user.getId(), user.getEmail(), user.getNickname());
        return Result.ok(TokenVO.of(tokenService.generate(loginUser), tokenService.expiresInSeconds(),
                toVO(user)));
    }

    private static UserVO toVO(MallUser user) {
        return new UserVO(user.getId(), user.getEmail(), user.getNickname());
    }
}
