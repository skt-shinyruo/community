package com.nowcoder.community.market.domain.model;

public final class MarketWalletActionStatus {

    public static final String PENDING = "PENDING";
    public static final String PROCESSING = "PROCESSING";
    public static final String RETRYING = "RETRYING";
    public static final String SUCCEEDED = "SUCCEEDED";
    public static final String CANCELLED = "CANCELLED";
    public static final String FAILED = "FAILED";
    public static final String DEAD = "DEAD";

    private MarketWalletActionStatus() {
    }
}
