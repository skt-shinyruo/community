package com.nowcoder.community.wallet.application.command;

import java.util.UUID;

public record AdminReverseTxnCommand(UUID actorUserId, String txnRef, String reason) {
}
