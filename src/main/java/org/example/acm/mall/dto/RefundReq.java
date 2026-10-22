package org.example.acm.mall.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "退款请求")
public record RefundReq(

        @Schema(description = "退款原因", example = "买错了")
        @Size(max = 128, message = "退款原因过长")
        String reason) {
}
