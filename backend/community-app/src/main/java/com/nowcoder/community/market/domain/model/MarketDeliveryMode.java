package com.nowcoder.community.market.domain.model;

import java.util.Arrays;

public enum MarketDeliveryMode implements MarketCodeEnum {
    MANUAL("MANUAL"),
    PRELOADED("PRELOADED");

    private final String code;

    MarketDeliveryMode(String code) {
        this.code = code;
    }

    @Override
    public String code() {
        return code;
    }

    public boolean isManual() {
        return this == MANUAL;
    }

    public boolean isPreloaded() {
        return this == PRELOADED;
    }

    public static MarketDeliveryMode fromCode(String code) {
        return Arrays.stream(values())
                .filter(mode -> mode.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown market delivery mode: " + code));
    }
}
