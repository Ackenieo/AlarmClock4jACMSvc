package org.example.acm.mall.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import org.example.acm.mall.enums.RefundStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("mall_refund")
public class MallRefund {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;

    private String outTradeNo;

    /** 退款请求号，同一笔退款重试靠它幂等 */
    private String outRequestNo;

    private BigDecimal refundAmount;

    private String reason;

    private RefundStatus status;

    private Integer fundChange;

    private String tradeNo;

    private LocalDateTime gmtRefund;

    private String rawResponse;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
