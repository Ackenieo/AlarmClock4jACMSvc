package org.example.acm.mall.enums;

public enum OrderStatus {

    WAITING_PAY("待支付"),
    PAID("已支付"),
    CLOSED("已关闭"),
    REFUNDED("已退款");

    private final String label;

    OrderStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public boolean isPaid() {
        return this == PAID;
    }

    /** 待支付只能走向已支付或已关闭；已支付可退款。终态之间不可互转 */
    public static boolean canTransit(OrderStatus from, OrderStatus to) {
        if (from == null || to == null) {
            return false;
        }
        return switch (from) {
            case WAITING_PAY -> to == PAID || to == CLOSED;
            case PAID -> to == REFUNDED;
            case CLOSED, REFUNDED -> false;
        };
    }
}
