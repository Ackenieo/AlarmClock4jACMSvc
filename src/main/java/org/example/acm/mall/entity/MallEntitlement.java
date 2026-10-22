package org.example.acm.mall.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import org.example.acm.mall.enums.EntitlementStatus;
import org.example.acm.mall.enums.ProductCategory;

import java.time.LocalDateTime;

/**
 * 用户已解锁的虚拟内容。UNIQUE(user_id, product_id) 是重复发货的最后一道锁。
 */
@Data
@TableName("mall_entitlement")
public class MallEntitlement {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long productId;

    private ProductCategory category;

    private Long orderId;

    private String outTradeNo;

    private EntitlementStatus status;

    private LocalDateTime grantedTime;

    private LocalDateTime revokedTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
