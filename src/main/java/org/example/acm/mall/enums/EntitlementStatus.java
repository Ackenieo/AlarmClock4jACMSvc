package org.example.acm.mall.enums;

public enum EntitlementStatus {

    ACTIVE("已解锁"),
    REVOKED("已回收");

    private final String label;

    EntitlementStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
