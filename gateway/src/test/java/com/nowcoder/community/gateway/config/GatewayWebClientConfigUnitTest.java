package com.nowcoder.community.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayWebClientConfigUnitTest {

    @Test
    void shouldCreateWebClientBeansWithoutStartingServer() {
        GatewayWebClientProperties properties = new GatewayWebClientProperties();
        properties.setConnectTimeoutMs(1000);
        properties.setResponseTimeoutMs(2000);
        properties.setReadTimeoutMs(2000);
        properties.setWriteTimeoutMs(2000);

        GatewayWebClientConfig config = new GatewayWebClientConfig();
        ConnectionProvider provider = config.gatewayWebClientConnectionProvider(properties);
        assertThat(provider).isNotNull();

        HttpClient httpClient = config.gatewayWebClientHttpClient(properties, provider);
        assertThat(httpClient).isNotNull();

        ReactorClientHttpConnector connector = config.gatewayWebClientConnector(httpClient);
        assertThat(connector).isNotNull();

        WebClient.Builder builder = config.loadBalancedWebClientBuilder(connector);
        assertThat(builder).isNotNull();
    }
}

