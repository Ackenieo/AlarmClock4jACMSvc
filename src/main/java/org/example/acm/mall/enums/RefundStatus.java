package org.example.acm.mall.enums;

public enum RefundStatus {

    PROCESSING("处理中"),
    SUCCESS("退款成功"),
    FAIL("退款失败");

    private final String label;

    RefundStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
