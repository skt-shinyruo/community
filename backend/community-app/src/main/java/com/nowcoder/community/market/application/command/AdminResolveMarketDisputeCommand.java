package com.nowcoder.community.market.application.command;

import java.util.UUID;

public record AdminResolveMarketDisputeCommand(
        UUID disputeId,
        UUID actorUserId,
        String note
) {
}
