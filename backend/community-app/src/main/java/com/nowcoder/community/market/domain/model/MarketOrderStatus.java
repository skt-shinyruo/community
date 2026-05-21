package com.nowcoder.community.market.domain.model;

import java.util.Arrays;
import java.util.Set;

public enum MarketOrderStatus implements MarketCodeEnum {
    ESCROW_PENDING("ESCROW_PENDING"),
    ESCROWED("ESCROWED"),
    DELIVERED("DELIVERED"),
    SHIPPED("SHIPPED"),
    RELEASE_PENDING("RELEASE_PENDING"),
    COMPLETED("COMPLETED"),
    REFUND_PENDING("REFUND_PENDING"),
    CANCELLED("CANCELLED"),
    ESCROW_CANCEL_PENDING("ESCROW_CANCEL_PENDING"),
    ESCROW_FAILED("ESCROW_FAILED"),
    DISPUTED("DISPUTED"),
    DISPUTE_REFUND_PENDING("DISPUTE_REFUND_PENDING"),
    DISPUTE_RELEASE_PENDING("DISPUTE_RELEASE_PENDING"),
    REFUNDED("REFUNDED");

    private static final Set<MarketOrderStatus> CONFIRMABLE = Set.of(DELIVERED, SHIPPED);
    private static final Set<MarketOrderStatus> DISPUTABLE = Set.of(DELIVERED, SHIPPED);

    private final String code;

    MarketOrderStatus(String code) {
        this.code = code;
    }

    @Override
    public String code() {
        return code;
    }

    public boolean isConfirmable() {
        return CONFIRMABLE.contains(this);
    }

    public boolean isDisputable() {
        return DISPUTABLE.contains(this);
    }

    public String pendingWalletActionType() {
        return switch (this) {
            case ESCROW_PENDING, ESCROW_CANCEL_PENDING -> MarketWalletActionType.ESCROW;
            case RELEASE_PENDING, DISPUTE_RELEASE_PENDING -> MarketWalletActionType.RELEASE;
            case REFUND_PENDING, DISPUTE_REFUND_PENDING -> MarketWalletActionType.REFUND;
            default -> null;
        };
    }

    public static MarketOrderStatus fromCode(String code) {
        return Arrays.stream(values())
                .filter(status -> status.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown market order status: " + code));
    }
}
