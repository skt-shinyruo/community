package com.nowcoder.community.runtime.application;

import com.nowcoder.community.runtime.application.result.RuntimeConfigResult;
import com.nowcoder.community.runtime.config.RuntimeConfigProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.mock.env.MockEnvironment;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeConfigApplicationServiceTest {

    @Test
    void exposesOnlyBrowserSafeFields() {
        RuntimeConfigProperties properties = new RuntimeConfigProperties();
        properties.setApiBasePath("/api");
        properties.setPublicGatewayOrigin("http://localhost:12880");
        properties.setWebsocketUrl("ws://localhost:12880/ws/im");
        properties.setAnalyticsEnabled(false);
        properties.setAnalyticsSampleRate(0.0);
        properties.setReleaseChannel("local");
        properties.getFeatures().put("file-upload", true);

        RuntimeConfigResult result = new RuntimeConfigApplicationService(properties).current();

        assertThat(result.apiBasePath()).isEqualTo("/api");
        assertThat(result.publicGatewayOrigin()).isEqualTo("http://localhost:12880");
        assertThat(result.websocketUrl()).isEqualTo("ws://localhost:12880/ws/im");
        assertThat(result.analyticsEnabled()).isFalse();
        assertThat(result.analyticsSampleRate()).isZero();
        assertThat(result.releaseChannel()).isEqualTo("local");
        assertThat(result.features()).containsExactly(Map.entry("file-upload", true));
    }

    @Test
    void bindsFrontendRuntimeProperties() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("frontend.runtime.api-base-path", "/api")
                .withProperty("frontend.runtime.public-gateway-origin", "http://localhost:12880")
                .withProperty("frontend.runtime.websocket-url", "ws://localhost:12880/ws/im")
                .withProperty("frontend.runtime.analytics-enabled", "true")
                .withProperty("frontend.runtime.analytics-sample-rate", "0.25")
                .withProperty("frontend.runtime.release-channel", "canary")
                .withProperty("frontend.runtime.features.file-upload", "true");

        RuntimeConfigProperties properties = Binder.get(environment)
                .bind("frontend.runtime", RuntimeConfigProperties.class)
                .orElseThrow(IllegalStateException::new);

        RuntimeConfigResult result = new RuntimeConfigApplicationService(properties).current();

        assertThat(result.apiBasePath()).isEqualTo("/api");
        assertThat(result.publicGatewayOrigin()).isEqualTo("http://localhost:12880");
        assertThat(result.websocketUrl()).isEqualTo("ws://localhost:12880/ws/im");
        assertThat(result.analyticsEnabled()).isTrue();
        assertThat(result.analyticsSampleRate()).isEqualTo(0.25);
        assertThat(result.releaseChannel()).isEqualTo("canary");
        assertThat(result.features()).containsExactly(Map.entry("file-upload", true));
    }
}
