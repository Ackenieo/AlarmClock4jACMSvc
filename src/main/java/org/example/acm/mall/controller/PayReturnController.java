package org.example.acm.mall.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 支付完成后的浏览器跳转落地页。
 * 这里的参数是支付宝拼在 URL 上的，没有签名保护，所以只显示、绝不据此改订单状态。
 */
@Tag(name = "05-支付宝回调", description = "同步跳转落地页，仅展示")
@RestController
@RequestMapping("/api/pay")
public class PayReturnController {

    @Operation(summary = "支付跳转回站", description = """
            return_url 是浏览器 302，本地也能收到，但参数无签名不可信。
            真正的确认必须走 POST /api/orders/{id}/sync（主动查单）或验签后的异步通知。""")
    @GetMapping(value = "/return", produces = MediaType.TEXT_HTML_VALUE)
    public String returnUrl(@RequestParam(required = false) String out_trade_no,
                            @RequestParam(required = false) String trade_status,
                            @RequestParam(required = false) String total_amount) {
        return """
                <!DOCTYPE html><html lang="zh-CN"><head><meta charset="utf-8"><title>支付宝已返回</title></head>
                <body style="font-family:sans-serif;max-width:640px;margin:3rem auto;line-height:1.7">
                <h2>支付宝已把浏览器跳回本站</h2>
                <p>订单号：%s</p>
                <p>报文中状态：%s</p>
                <p>报文金额：%s</p>
                <p><strong>这些参数没有签名保护，不能用来判定支付成功。</strong></p>
                <p>请回到接口里调用 <code>POST /api/orders/{id}/sync</code> 主动查单完成确认，
                确认后对应单词本或皮肤会自动解锁。</p>
                </body></html>
                """.formatted(escape(out_trade_no), escape(trade_status), escape(total_amount));
    }

    private static String escape(String value) {
        if (value == null) {
            return "-";
        }
        // 支付宝返回的参数不可信，直接拼进 HTML 会被 XSS
        return value.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }
}
