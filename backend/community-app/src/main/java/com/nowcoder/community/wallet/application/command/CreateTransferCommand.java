package com.nowcoder.community.wallet.application.command;

import java.util.UUID;

public record CreateTransferCommand(UUID fromUserId, UUID toUserId, long amount, String requestId, String idempotencyKey) {
}
