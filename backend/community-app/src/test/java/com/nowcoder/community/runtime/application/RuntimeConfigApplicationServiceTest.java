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
        properties.getUpload().setMaxFileSize("10GB");
        properties.getUpload().setMaxRequestSize("10GB");
        properties.getUpload().getAllowedMimeTypes().add("image/png");
        properties.getUpload().getAllowedExtensions().add("png");
        properties.getUpload().setAvatarUploadEnabled(true);
        properties.getUpload().setMediaUploadEnabled(true);

        RuntimeConfigResult result = new RuntimeConfigApplicationService(properties).current();

        assertThat(result.apiBasePath()).isEqualTo("/api");
        assertThat(result.publicGatewayOrigin()).isEqualTo("http://localhost:12880");
        assertThat(result.websocketUrl()).isEqualTo("ws://localhost:12880/ws/im");
        assertThat(result.analyticsEnabled()).isFalse();
        assertThat(result.analyticsSampleRate()).isZero();
        assertThat(result.releaseChannel()).isEqualTo("local");
        assertThat(result.features()).containsExactly(Map.entry("file-upload", true));
        assertThat(result.upload().maxFileSize()).isEqualTo("10GB");
        assertThat(result.upload().maxRequestSize()).isEqualTo("10GB");
        assertThat(result.upload().allowedMimeTypes()).containsExactly("image/png");
        assertThat(result.upload().allowedExtensions()).containsExactly("png");
        assertThat(result.upload().avatarUploadEnabled()).isTrue();
        assertThat(result.upload().mediaUploadEnabled()).isTrue();
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
                .withProperty("frontend.runtime.features.file-upload", "true")
                .withProperty("frontend.runtime.upload.max-file-size", "8GB")
                .withProperty("frontend.runtime.upload.max-request-size", "9GB")
                .withProperty("frontend.runtime.upload.allowed-mime-types[0]", "image/webp")
                .withProperty("frontend.runtime.upload.allowed-extensions[0]", "webp")
                .withProperty("frontend.runtime.upload.avatar-upload-enabled", "false")
                .withProperty("frontend.runtime.upload.media-upload-enabled", "true");

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
        assertThat(result.upload().maxFileSize()).isEqualTo("8GB");
        assertThat(result.upload().maxRequestSize()).isEqualTo("9GB");
        assertThat(result.upload().allowedMimeTypes()).containsExactly("image/webp");
        assertThat(result.upload().allowedExtensions()).containsExactly("webp");
        assertThat(result.upload().avatarUploadEnabled()).isFalse();
        assertThat(result.upload().mediaUploadEnabled()).isTrue();
    }
}
