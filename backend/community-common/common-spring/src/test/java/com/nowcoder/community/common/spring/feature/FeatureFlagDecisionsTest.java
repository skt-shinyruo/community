package com.nowcoder.community.common.spring.feature;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.mock.env.MockEnvironment;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeatureFlagDecisionsTest {

    @Test
    void configuredFlagReturnsTrue() {
        FeatureFlagProperties properties = new FeatureFlagProperties();
        properties.getFlags().put("content.publish", true);

        FeatureFlagDecisions decisions = new FeatureFlagDecisions(properties);

        assertTrue(decisions.enabled("content.publish"));
    }

    @Test
    void unknownFlagDefaultsFalse() {
        FeatureFlagProperties properties = new FeatureFlagProperties();
        properties.getFlags().putAll(Map.of("content.publish", true));

        FeatureFlagDecisions decisions = new FeatureFlagDecisions(properties);

        assertFalse(decisions.enabled("content.audit"));
    }

    @Test
    void unknownFlagCanUseCallerDefault() {
        FeatureFlagDecisions decisions = new FeatureFlagDecisions(new FeatureFlagProperties());

        assertTrue(decisions.enabledOrDefault("search", true));
        assertFalse(decisions.enabledOrDefault("analytics-ingest", false));
    }

    @Test
    void bindsSeedStyleFeatureProperties() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("community.features.post-publishing", "true");
        FeatureFlagProperties properties = Binder.get(environment)
                .bind("community", FeatureFlagProperties.class)
                .orElseThrow(IllegalStateException::new);

        FeatureFlagDecisions decisions = new FeatureFlagDecisions(properties);

        assertTrue(decisions.enabled("post-publishing"));
    }
}
