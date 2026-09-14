package com.nowcoder.community.im.gateway.session;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PublicWsUrlFactoryTest {

    @Test
    void shouldUseConfiguredAbsolutePublicWsUrlWhenPresent() {
        ImGatewaySessionProperties properties = new ImGatewaySessionProperties();
        properties.setPublicWsUrl("wss://community.example/ws/im");
        PublicWsUrlFactory factory = new PublicWsUrlFactory(properties);

        String url = factory.build();

        assertThat(url).isEqualTo("wss://community.example/ws/im");
    }

    @Test
    void shouldRejectMissingConfiguredPublicWsUrl() {
        ImGatewaySessionProperties properties = new ImGatewaySessionProperties();
        PublicWsUrlFactory factory = new PublicWsUrlFactory(properties);

        assertThatThrownBy(factory::build)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("publicWsUrl");
    }

    @Test
    void shouldRejectInvalidConfiguredPublicWsUrl() {
        ImGatewaySessionProperties properties = new ImGatewaySessionProperties();
        properties.setPublicWsUrl("https://community.example/ws/im");
        PublicWsUrlFactory factory = new PublicWsUrlFactory(properties);

        assertThatThrownBy(factory::build)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("publicWsUrl");
    }
}
