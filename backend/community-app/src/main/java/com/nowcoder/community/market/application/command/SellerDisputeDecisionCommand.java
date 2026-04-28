package com.nowcoder.community.market.application.command;

import java.util.UUID;

public record SellerDisputeDecisionCommand(
        UUID disputeId,
        UUID sellerUserId,
        String note
) {
}
