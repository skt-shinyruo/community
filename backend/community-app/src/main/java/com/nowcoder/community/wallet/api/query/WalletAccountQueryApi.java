package com.nowcoder.community.wallet.api.query;

public interface WalletAccountQueryApi {

    long balanceOfUser(long userId);

    String statusOfUser(long userId);
}
