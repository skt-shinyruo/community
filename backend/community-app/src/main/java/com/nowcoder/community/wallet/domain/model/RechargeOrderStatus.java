package com.nowcoder.community.wallet.domain.model;

import java.util.Arrays;

public enum RechargeOrderStatus {
    CREATED("CREATED"),
    PAID("PAID");

    private final String code;

    RechargeOrderStatus(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static RechargeOrderStatus fromCode(String code) {
        return Arrays.stream(values())
                .filter(status -> status.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown recharge order status: " + code));
    }
}
