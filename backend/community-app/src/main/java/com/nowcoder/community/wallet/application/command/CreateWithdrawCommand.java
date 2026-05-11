package com.nowcoder.community.wallet.application.command;

import java.util.UUID;

public record CreateWithdrawCommand(UUID userId, long amount, String idempotencyKey) {
}
