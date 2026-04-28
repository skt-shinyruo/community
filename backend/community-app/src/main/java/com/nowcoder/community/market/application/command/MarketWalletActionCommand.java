package com.nowcoder.community.market.application.command;

import java.util.UUID;

public record MarketWalletActionCommand(
        UUID orderId,
        UUID disputeId,
        String actionType,
        UUID actorUserId,
        UUID counterpartyUserId,
        long amount
) {
}
