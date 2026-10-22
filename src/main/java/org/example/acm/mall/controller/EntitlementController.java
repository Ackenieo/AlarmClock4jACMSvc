package org.example.acm.mall.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.acm.mall.common.Result;
import org.example.acm.mall.config.OpenApiConfig;
import org.example.acm.mall.security.CurrentUser;
import org.example.acm.mall.security.LoginUser;
import org.example.acm.mall.service.EntitlementService;
import org.example.acm.mall.vo.EntitlementVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "04-我的内容", description = "已解锁的单词本与皮肤，可直接用于客户端判断展示权限")
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
@RestController
@RequestMapping("/api/me")
public class EntitlementController {

    private final EntitlementService entitlementService;

    public EntitlementController(EntitlementService entitlementService) {
        this.entitlementService = entitlementService;
    }

    @Operation(summary = "我已解锁的内容",
            description = "支付确认后自动写入；退款后对应记录转为 REVOKED，客户端据此收回入口")
    @GetMapping("/entitlements")
    public Result<List<EntitlementVO>> listMine(@CurrentUser LoginUser me) {
        return Result.ok(entitlementService.listMine(me.id()));
    }
}
