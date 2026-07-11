package com.nowcoder.community.app.config;

import com.nowcoder.community.common.outbox.OutboxProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CanonicalEventBackboneGuardTest {

    @Test
    void disabledOutboxShouldFailFast() {
        OutboxProperties properties = new OutboxProperties();
        properties.setEnabled(false);

        assertThatThrownBy(() -> new CanonicalEventBackboneGuard(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("events.outbox.enabled must be true");
    }

    @Test
    void enabledOutboxShouldBeAccepted() {
        OutboxProperties properties = new OutboxProperties();
        properties.setEnabled(true);

        assertThatCode(() -> new CanonicalEventBackboneGuard(properties)).doesNotThrowAnyException();
    }
}
