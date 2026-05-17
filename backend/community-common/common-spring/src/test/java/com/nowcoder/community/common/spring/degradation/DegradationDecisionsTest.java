package com.nowcoder.community.common.spring.degradation;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.mock.env.MockEnvironment;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DegradationDecisionsTest {

    @Test
    void configuredModeReturnsValue() {
        DegradationProperties properties = new DegradationProperties();
        properties.getModes().put("content.feed", "best-effort");

        DegradationDecisions decisions = new DegradationDecisions(properties);

        assertEquals("best-effort", decisions.mode("content.feed"));
    }

    @Test
    void unknownDefaultsStrict() {
        DegradationProperties properties = new DegradationProperties();
        properties.getModes().putAll(Map.of("content.feed", "best-effort"));

        DegradationDecisions decisions = new DegradationDecisions(properties);

        assertEquals("strict", decisions.mode("content.search"));
    }

    @Test
    void bindsSeedStyleDegradationProperties() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("community.degradation.search", "best-effort");
        DegradationProperties properties = Binder.get(environment)
                .bind("community", DegradationProperties.class)
                .orElseThrow(IllegalStateException::new);

        DegradationDecisions decisions = new DegradationDecisions(properties);

        assertEquals("best-effort", decisions.mode("search"));
    }
}
