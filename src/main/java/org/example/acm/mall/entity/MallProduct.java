package org.example.acm.mall.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import org.example.acm.mall.enums.ProductCategory;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("mall_product")
public class MallProduct {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String description;

    private ProductCategory category;

    private BigDecimal price;

    private BigDecimal originalPrice;

    private String coverUrl;

    /** 单词本专用 */
    private Integer wordCount;

    /** 皮肤专用 */
    private String skinStyle;

    private Integer onSale;

    private Integer sortNo;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
