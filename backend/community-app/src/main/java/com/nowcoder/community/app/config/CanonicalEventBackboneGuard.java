package com.nowcoder.community.app.config;

import com.nowcoder.community.common.outbox.OutboxProperties;
import org.springframework.stereotype.Component;

@Component
public final class CanonicalEventBackboneGuard {

    public CanonicalEventBackboneGuard(OutboxProperties properties) {
        if (properties == null || !properties.isEnabled()) {
            throw new IllegalStateException(
                    "events.outbox.enabled must be true for the canonical event backbone");
        }
    }
}
