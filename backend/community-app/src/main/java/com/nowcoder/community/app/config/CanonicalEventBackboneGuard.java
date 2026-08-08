package com.nowcoder.community.app.config;

import com.nowcoder.community.common.outbox.OutboxProperties;
import com.nowcoder.community.infra.startup.ProductionEnvironmentPredicate;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public final class CanonicalEventBackboneGuard {

    public CanonicalEventBackboneGuard(OutboxProperties properties, Environment environment) {
        if (properties == null || !properties.isEnabled()) {
            throw new IllegalStateException(
                    "events.outbox.enabled must be true for the canonical event backbone");
        }
        if (ProductionEnvironmentPredicate.isProduction(environment) && !properties.isWorkerEnabled()) {
            throw new IllegalStateException(
                    "events.outbox.worker-enabled must be true in production");
        }
    }
}
