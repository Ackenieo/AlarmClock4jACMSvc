package org.example.acm.mall.enums;

/**
 * 商品类目。DB 列存枚举名，MyBatis 默认的按名称映射已正确；
 * 刻意不加 @EnumValue，也不设 default-enum-type-handler，见 application.yml 注释。
 */
public enum ProductCategory {

    WORD_BOOK("单词本"),
    APP_SKIN("应用皮肤");

    private final String label;

    ProductCategory(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
