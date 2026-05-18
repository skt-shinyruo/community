package com.nowcoder.community.wallet.application.command;

import java.util.UUID;

public record ListWalletTransactionsCommand(UUID userId, Integer limit) {
}
