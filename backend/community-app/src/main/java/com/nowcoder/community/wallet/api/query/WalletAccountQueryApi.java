package com.nowcoder.community.wallet.api.query;

import java.util.UUID;

public interface WalletAccountQueryApi {

    long balanceOfUser(UUID userId);

    String statusOfUser(UUID userId);
}
