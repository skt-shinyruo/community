package com.nowcoder.community.wallet.application;

import com.nowcoder.community.wallet.application.WalletTestCreditQuotaPort.Usage;
import com.nowcoder.community.wallet.application.result.WalletCapabilitiesResult;
import com.nowcoder.community.wallet.application.result.WalletCapabilitiesResult.Action;
import com.nowcoder.community.wallet.application.result.WalletCapabilitiesResult.TestCredits;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class WalletTestCreditCapabilityApplicationService {

    private final WalletTestCreditPolicy policy;
    private final WalletTestCreditQuotaPort quotaPort;

    public WalletTestCreditCapabilityApplicationService(WalletTestCreditPolicy policy,
                                                        WalletTestCreditQuotaPort quotaPort) {
        this.policy = policy;
        this.quotaPort = quotaPort;
    }

    public WalletCapabilitiesResult capabilities(UUID userId) {
        WalletTestCreditProperties properties = policy.properties();
        Usage usage = properties.isEnabled() ? quotaPort.usage(userId) : Usage.empty();
        Action grant = action(
                properties.isGrantAvailable(),
                properties.getMaxGrantPerRequest(),
                properties.getGrantQuotaPerUser(),
                usage.grantedAmount()
        );
        Action discard = action(
                properties.isDiscardAvailable(),
                properties.getMaxDiscardPerRequest(),
                properties.getDiscardQuotaPerUser(),
                usage.discardedAmount(),
                Math.max(0L, usage.grantedAmount() - usage.discardedAmount())
        );
        return new WalletCapabilitiesResult(
                "INTERNAL_TEST_CREDIT",
                false,
                false,
                new TestCredits(properties.isEnabled(), grant, discard)
        );
    }

    private Action action(boolean enabled, long maxPerRequest, long totalQuota, long usedAmount) {
        return action(enabled, maxPerRequest, totalQuota, usedAmount, Long.MAX_VALUE);
    }

    private Action action(boolean enabled,
                          long maxPerRequest,
                          long totalQuota,
                          long usedAmount,
                          long availableAmount) {
        long normalizedUsed = Math.max(0L, usedAmount);
        long quotaRemaining = Math.max(0L, totalQuota - Math.min(totalQuota, normalizedUsed));
        long remaining = Math.min(quotaRemaining, Math.max(0L, availableAmount));
        return new Action(enabled, maxPerRequest, totalQuota, normalizedUsed, remaining);
    }
}
