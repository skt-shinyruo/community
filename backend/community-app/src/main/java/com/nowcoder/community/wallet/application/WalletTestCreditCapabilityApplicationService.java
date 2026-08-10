package com.nowcoder.community.wallet.application;

import com.nowcoder.community.wallet.application.WalletTestCreditQuotaPort.Usage;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.UUID;

@Service
public class WalletTestCreditCapabilityApplicationService {

    public record WalletCapabilitiesResult(
            String balanceUnit,
            boolean realPaymentsSupported,
            boolean realPayoutsSupported,
            TestCredits testCredits
    ) {

        public record TestCredits(boolean enabled, Action grant, Action discard) {
        }

        public record Action(
                boolean enabled,
                long maxAmountPerRequest,
                long totalQuota,
                long usedAmount,
                long remainingAmount
        ) {
        }
    }

    private final WalletTestCreditPolicy policy;
    private final WalletTestCreditQuotaPort quotaPort;

    public WalletTestCreditCapabilityApplicationService(WalletTestCreditPolicy policy,
                                                        WalletTestCreditQuotaPort quotaPort) {
        this.policy = Objects.requireNonNull(policy, "policy must not be null");
        this.quotaPort = Objects.requireNonNull(quotaPort, "quotaPort must not be null");
    }

    public WalletCapabilitiesResult capabilities(UUID userId) {
        WalletTestCreditProperties properties = policy.properties();
        Usage usage = properties.isEnabled() ? quotaPort.usage(userId) : Usage.empty();
        WalletCapabilitiesResult.Action grant = action(
                properties.isGrantAvailable(),
                properties.getMaxGrantPerRequest(),
                properties.getGrantQuotaPerUser(),
                usage.grantedAmount()
        );
        WalletCapabilitiesResult.Action discard = action(
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
                new WalletCapabilitiesResult.TestCredits(properties.isEnabled(), grant, discard)
        );
    }

    private WalletCapabilitiesResult.Action action(boolean enabled, long maxPerRequest, long totalQuota, long usedAmount) {
        return action(enabled, maxPerRequest, totalQuota, usedAmount, Long.MAX_VALUE);
    }

    private WalletCapabilitiesResult.Action action(boolean enabled,
                                                   long maxPerRequest,
                                                   long totalQuota,
                                                   long usedAmount,
                                                   long availableAmount) {
        long normalizedUsed = Math.max(0L, usedAmount);
        long quotaRemaining = Math.max(0L, totalQuota - Math.min(totalQuota, normalizedUsed));
        long remaining = Math.min(quotaRemaining, Math.max(0L, availableAmount));
        return new WalletCapabilitiesResult.Action(enabled, maxPerRequest, totalQuota, normalizedUsed, remaining);
    }
}
