package com.nowcoder.community.gateway.edge;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;

import static org.assertj.core.api.Assertions.assertThat;

class TrafficPolicyEvaluatorTest {

    @Test
    void shouldReturnDefaultDecisionWhenNoRuleMatches() {
        TrafficPolicyProperties properties = new TrafficPolicyProperties();
        properties.setDefaultPolicyId("baseline");
        properties.getDefaultTags().put("plane", "http");

        TrafficPolicyEvaluator evaluator = new TrafficPolicyEvaluator(properties);

        TrafficPolicyEvaluator.Decision decision = evaluator.evaluate(
                MockServerHttpRequest.method(HttpMethod.GET, "/unmatched").build()
        );

        assertThat(decision.policyId()).isEqualTo("baseline");
        assertThat(decision.tags()).containsEntry("plane", "http");
    }

    @Test
    void shouldPreferMostSpecificEnabledRuleMatchingMethod() {
        TrafficPolicyProperties properties = new TrafficPolicyProperties();
        properties.setDefaultPolicyId("baseline");
        properties.getDefaultTags().put("plane", "http");

        TrafficPolicyProperties.Rule apiRead = new TrafficPolicyProperties.Rule();
        apiRead.setPolicyId("api-read");
        apiRead.getPathPrefixes().add("/api");
        apiRead.getMethods().add("GET");
        apiRead.getTags().put("surface", "api");

        TrafficPolicyProperties.Rule disabledLogin = new TrafficPolicyProperties.Rule();
        disabledLogin.setEnabled(false);
        disabledLogin.setPolicyId("disabled-login");
        disabledLogin.getPathPrefixes().add("/api/auth/login");
        disabledLogin.getMethods().add("POST");
        disabledLogin.getTags().put("surface", "disabled");

        TrafficPolicyProperties.Rule authWrite = new TrafficPolicyProperties.Rule();
        authWrite.setPolicyId("auth-write");
        authWrite.getPathPrefixes().add("/api/auth");
        authWrite.getMethods().add("POST");
        authWrite.getTags().put("surface", "auth");
        authWrite.getTags().put("risk", "write");

        properties.getRules().add(apiRead);
        properties.getRules().add(disabledLogin);
        properties.getRules().add(authWrite);

        TrafficPolicyEvaluator evaluator = new TrafficPolicyEvaluator(properties);

        TrafficPolicyEvaluator.Decision postDecision = evaluator.evaluate(
                MockServerHttpRequest.method(HttpMethod.POST, "/api/auth/login").build()
        );
        assertThat(postDecision.policyId()).isEqualTo("auth-write");
        assertThat(postDecision.tags())
                .containsEntry("plane", "http")
                .containsEntry("surface", "auth")
                .containsEntry("risk", "write");

        TrafficPolicyEvaluator.Decision getDecision = evaluator.evaluate(
                MockServerHttpRequest.method(HttpMethod.GET, "/api/auth/login").build()
        );
        assertThat(getDecision.policyId()).isEqualTo("api-read");
        assertThat(getDecision.tags())
                .containsEntry("plane", "http")
                .containsEntry("surface", "api")
                .doesNotContainKey("risk");
    }
}
