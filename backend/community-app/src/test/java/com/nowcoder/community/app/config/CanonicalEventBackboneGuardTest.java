package com.nowcoder.community.app.config;

import com.nowcoder.community.common.outbox.OutboxProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CanonicalEventBackboneGuardTest {

    @Test
    void disabledOutboxShouldFailFast() {
        OutboxProperties properties = new OutboxProperties();
        properties.setEnabled(false);

        assertThatThrownBy(() -> new CanonicalEventBackboneGuard(properties, new MockEnvironment()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("events.outbox.enabled must be true");
    }

    @Test
    void enabledOutboxShouldBeAccepted() {
        OutboxProperties properties = new OutboxProperties();
        properties.setEnabled(true);

        assertThatCode(() -> new CanonicalEventBackboneGuard(properties, new MockEnvironment()))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldRejectDisabledWorkerInProduction() {
        OutboxProperties properties = new OutboxProperties();
        properties.setEnabled(true);
        properties.setWorkerEnabled(false);

        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");

        assertThatThrownBy(() -> new CanonicalEventBackboneGuard(properties, environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("events.outbox.worker-enabled must be true in production");
    }

    @Test
    void shouldRecognizeProductionProfileCaseInsensitively() {
        OutboxProperties properties = new OutboxProperties();
        properties.setEnabled(true);
        properties.setWorkerEnabled(false);

        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("ProDucTion");

        assertThatThrownBy(() -> new CanonicalEventBackboneGuard(properties, environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("events.outbox.worker-enabled must be true in production");
    }

    @Test
    void shouldRejectDisabledWorkerInProductionDeploymentEnvironmentWithDevProfile() {
        OutboxProperties properties = new OutboxProperties();
        properties.setEnabled(true);
        properties.setWorkerEnabled(false);

        MockEnvironment environment = new MockEnvironment()
                .withProperty("DEPLOYMENT_ENVIRONMENT", "production");
        environment.setActiveProfiles("dev");

        assertThatThrownBy(() -> new CanonicalEventBackboneGuard(properties, environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("events.outbox.worker-enabled must be true in production");
    }

    @Test
    void shouldAllowDisabledWorkerOutsideProduction() {
        OutboxProperties properties = new OutboxProperties();
        properties.setEnabled(true);
        properties.setWorkerEnabled(false);

        assertThatCode(() -> new CanonicalEventBackboneGuard(properties, new MockEnvironment()))
                .doesNotThrowAnyException();
    }
}
