package com.nowcoder.community.market.domain.model;

import java.util.Arrays;

public enum MarketGoodsType implements MarketCodeEnum {
    PHYSICAL("PHYSICAL"),
    VIRTUAL("VIRTUAL");

    private final String code;

    MarketGoodsType(String code) {
        this.code = code;
    }

    @Override
    public String code() {
        return code;
    }

    public boolean isPhysical() {
        return this == PHYSICAL;
    }

    public boolean isVirtual() {
        return this == VIRTUAL;
    }

    public static MarketGoodsType fromCode(String code) {
        return Arrays.stream(values())
                .filter(type -> type.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown market goods type: " + code));
    }
}
