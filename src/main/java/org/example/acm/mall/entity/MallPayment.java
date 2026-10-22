package org.example.acm.mall.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("mall_payment")
public class MallPayment {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;

    private String outTradeNo;

    private String tradeNo;

    private String channel;

    private String tradeStatus;

    private BigDecimal totalAmount;

    private BigDecimal refundAmount;

    private String buyerUserId;

    private LocalDateTime gmtPayment;

    private LocalDateTime lastSyncTime;

    /** 验签通过的异步通知原文 */
    private String notifyContent;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
