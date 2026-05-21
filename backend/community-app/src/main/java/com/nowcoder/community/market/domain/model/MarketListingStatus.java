package com.nowcoder.community.market.domain.model;

import java.util.Arrays;

public enum MarketListingStatus implements MarketCodeEnum {
    ACTIVE("ACTIVE"),
    SOLD_OUT("SOLD_OUT");

    private final String code;

    MarketListingStatus(String code) {
        this.code = code;
    }

    @Override
    public String code() {
        return code;
    }

    public static MarketListingStatus fromCode(String code) {
        return Arrays.stream(values())
                .filter(status -> status.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown market listing status: " + code));
    }
}
