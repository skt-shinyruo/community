package com.nowcoder.community.market.domain.model;

import java.util.Arrays;

public enum MarketStockMode implements MarketCodeEnum {
    FINITE("FINITE"),
    UNLIMITED("UNLIMITED");

    private final String code;

    MarketStockMode(String code) {
        this.code = code;
    }

    @Override
    public String code() {
        return code;
    }

    public boolean isFinite() {
        return this == FINITE;
    }

    public static MarketStockMode fromCode(String code) {
        return Arrays.stream(values())
                .filter(mode -> mode.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown market stock mode: " + code));
    }
}
