package org.example.acm.mall.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import org.example.acm.mall.enums.OrderStatus;
import org.example.acm.mall.enums.ProductCategory;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("mall_order")
public class MallOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 商户订单号，直接作为支付宝 out_trade_no */
    private String orderNo;

    private Long userId;

    private Long productId;

    // 以下四列是下单时的商品快照，商品改价或下架都不该影响历史订单
    private String productName;

    private ProductCategory category;

    private BigDecimal unitPrice;

    private BigDecimal payAmount;

    private Integer quantity;

    private OrderStatus status;

    private LocalDateTime expireTime;

    private LocalDateTime paidTime;

    private LocalDateTime closedTime;

    private LocalDateTime refundedTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
