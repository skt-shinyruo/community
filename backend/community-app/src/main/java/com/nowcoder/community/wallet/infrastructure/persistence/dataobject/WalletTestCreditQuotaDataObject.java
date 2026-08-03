package com.nowcoder.community.wallet.infrastructure.persistence.dataobject;

import java.util.UUID;

public class WalletTestCreditQuotaDataObject {

    private UUID userId;
    private long grantedAmount;
    private long discardedAmount;

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public long getGrantedAmount() {
        return grantedAmount;
    }

    public void setGrantedAmount(long grantedAmount) {
        this.grantedAmount = grantedAmount;
    }

    public long getDiscardedAmount() {
        return discardedAmount;
    }

    public void setDiscardedAmount(long discardedAmount) {
        this.discardedAmount = discardedAmount;
    }
}
