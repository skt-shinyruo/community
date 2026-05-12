package com.nowcoder.community.im.gateway.session;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PublicWsUrlFactoryTest {

    @Test
    void shouldUseConfiguredAbsolutePublicWsUrlWhenPresent() {
        ImGatewaySessionProperties properties = new ImGatewaySessionProperties();
        properties.setPublicWsUrl("wss://community.example/ws/im");
        PublicWsUrlFactory factory = new PublicWsUrlFactory(properties);

        String url = factory.build(MockServerHttpRequest.get("http://internal/api/im/sessions").build());

        assertThat(url).isEqualTo("wss://community.example/ws/im");
    }

    @Test
    void shouldRejectMissingConfiguredPublicWsUrl() {
        ImGatewaySessionProperties properties = new ImGatewaySessionProperties();
        PublicWsUrlFactory factory = new PublicWsUrlFactory(properties);

        assertThatThrownBy(() -> factory.build(MockServerHttpRequest.post("http://community-im-gateway:18083/api/im/sessions")
                .header("X-Forwarded-Proto", "https")
                .header("X-Forwarded-Host", "community.example")
                .build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("publicWsUrl");
    }

    @Test
    void shouldRejectInvalidConfiguredPublicWsUrl() {
        ImGatewaySessionProperties properties = new ImGatewaySessionProperties();
        properties.setPublicWsUrl("https://community.example/ws/im");
        PublicWsUrlFactory factory = new PublicWsUrlFactory(properties);

        assertThatThrownBy(() -> factory.build(MockServerHttpRequest.get("http://internal/api/im/sessions").build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("publicWsUrl");
    }

    @Test
    void shouldIgnoreRequestAuthorityWhenConfiguredPublicWsUrlIsPresent() {
        ImGatewaySessionProperties properties = new ImGatewaySessionProperties();
        properties.setPublicWsUrl("ws://localhost:12880/ws/im");
        PublicWsUrlFactory factory = new PublicWsUrlFactory(properties);

        String url = factory.build(MockServerHttpRequest.post("http://community-im-gateway:18083/api/im/sessions")
                .header("X-Forwarded-Proto", "https")
                .header("X-Forwarded-Host", "attacker.example/path?ticket=leak")
                .build());

        assertThat(url).isEqualTo("ws://localhost:12880/ws/im");
        assertThat(url).doesNotContain("attacker.example");
    }
}
