package com.nowcoder.community.market.domain.model;

import java.util.Objects;
import java.util.UUID;

public record MarketWalletActionLease(UUID actionId, UUID token) {

    public MarketWalletActionLease {
        Objects.requireNonNull(actionId, "actionId must not be null");
        Objects.requireNonNull(token, "token must not be null");
    }
}
