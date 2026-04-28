package com.nowcoder.community.wallet.application.command;

import java.util.UUID;

public record CreateRechargeCommand(UUID userId, long amount, String requestId, String idempotencyKey) {
}
