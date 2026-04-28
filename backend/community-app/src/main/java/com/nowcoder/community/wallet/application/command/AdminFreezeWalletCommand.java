package com.nowcoder.community.wallet.application.command;

import java.util.UUID;

public record AdminFreezeWalletCommand(UUID actorUserId, UUID userId, String reason) {
}
