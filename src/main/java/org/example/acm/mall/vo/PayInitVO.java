package org.example.acm.mall.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "发起支付的结果")
public record PayInitVO(
        String orderNo,
        @Schema(description = "应付金额（元）") BigDecimal payAmount,
        @Schema(description = "GET = 已签名的网关跳转链接；POST = 自动提交表单 HTML") String method,
        @Schema(description = "method=GET 时给出，复制到浏览器即可打开沙箱收银台") String payUrl,
        @Schema(description = "method=POST 时给出，需另存为 .html 打开") String payFormHtml,
        @Schema(description = "操作提示") String tip) {
}
