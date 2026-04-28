package com.nowcoder.community.market.application.command;

import java.util.UUID;

public record CreateMarketDisputeCommand(
        UUID orderId,
        UUID buyerUserId,
        String reason,
        String buyerNote
) {
}
