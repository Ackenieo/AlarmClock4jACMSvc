package org.example.acm.mall.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.acm.mall.common.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@Tag(name = "00-健康检查", description = "确认服务已启动，无需鉴权")
@RestController
public class PingController {

    @Operation(summary = "探活", description = "用于确认应用已起来、拦截器白名单配置正确")
    @GetMapping("/api/ping")
    public Result<String> ping() {
        return Result.ok("pong " + LocalDateTime.now());
    }
}
