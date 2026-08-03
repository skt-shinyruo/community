package com.nowcoder.community.wallet.application;

import com.nowcoder.community.wallet.domain.service.WalletAmountPolicy;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "wallet.test-credits")
public class WalletTestCreditProperties {

    private boolean enabled;
    private boolean grantEnabled;
    private boolean discardEnabled;
    private long maxGrantPerRequest = 1_000L;
    private long maxDiscardPerRequest = 1_000L;
    private long grantQuotaPerUser = 5_000L;
    private long discardQuotaPerUser = 5_000L;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isGrantEnabled() {
        return grantEnabled;
    }

    public void setGrantEnabled(boolean grantEnabled) {
        this.grantEnabled = grantEnabled;
    }

    public boolean isDiscardEnabled() {
        return discardEnabled;
    }

    public void setDiscardEnabled(boolean discardEnabled) {
        this.discardEnabled = discardEnabled;
    }

    public long getMaxGrantPerRequest() {
        return Math.min(maxGrantPerRequest, grantQuotaPerUser);
    }

    public void setMaxGrantPerRequest(long maxGrantPerRequest) {
        this.maxGrantPerRequest = normalizeAmount(maxGrantPerRequest);
    }

    public long getMaxDiscardPerRequest() {
        return Math.min(maxDiscardPerRequest, discardQuotaPerUser);
    }

    public void setMaxDiscardPerRequest(long maxDiscardPerRequest) {
        this.maxDiscardPerRequest = normalizeAmount(maxDiscardPerRequest);
    }

    public long getGrantQuotaPerUser() {
        return grantQuotaPerUser;
    }

    public void setGrantQuotaPerUser(long grantQuotaPerUser) {
        this.grantQuotaPerUser = normalizeAmount(grantQuotaPerUser);
    }

    public long getDiscardQuotaPerUser() {
        return discardQuotaPerUser;
    }

    public void setDiscardQuotaPerUser(long discardQuotaPerUser) {
        this.discardQuotaPerUser = normalizeAmount(discardQuotaPerUser);
    }

    public boolean isGrantAvailable() {
        return enabled && grantEnabled;
    }

    public boolean isDiscardAvailable() {
        return enabled && discardEnabled;
    }

    private long normalizeAmount(long amount) {
        return Math.max(1L, Math.min(amount, WalletAmountPolicy.MAX_AMOUNT));
    }
}
