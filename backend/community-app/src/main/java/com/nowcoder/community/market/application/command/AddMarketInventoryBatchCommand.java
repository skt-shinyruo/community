package com.nowcoder.community.market.application.command;

import java.util.List;
import java.util.UUID;

public record AddMarketInventoryBatchCommand(
        UUID listingId,
        UUID sellerUserId,
        String payloadType,
        List<String> payloads
) {
}
