package com.nowcoder.community.im.gateway.ws;

import com.nowcoder.community.im.gateway.security.ImGatewayCorsConfig;
import com.nowcoder.community.im.gateway.security.ImGatewayCorsProperties;
import com.nowcoder.community.im.gateway.session.ImGatewaySessionProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter;
import org.springframework.web.server.ServerWebExchange;

import java.util.Map;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({ImGatewaySessionProperties.class, ImGatewayCorsProperties.class})
public class ImGatewayWebSocketConfig {

    @Bean
    HandlerMapping imGatewayWebSocketMapping(
            ExternalImEdgeWebSocketHandler handler,
            ImGatewaySessionProperties properties,
            ImGatewayCorsProperties corsProperties
    ) {
        String path = properties.getWs().getPath();
        SimpleUrlHandlerMapping mapping = new SimpleUrlHandlerMapping();
        mapping.setOrder(-1);
        mapping.setUrlMap(Map.of(path, handler));
        mapping.setHandlerPredicate((candidate, exchange) -> isWebSocketUpgrade(exchange));
        CorsConfiguration wsCors = ImGatewayCorsConfig.buildCorsConfiguration(corsProperties);
        mapping.setCorsConfigurations(Map.of(path, wsCors));
        return mapping;
    }

    @Bean
    WebSocketHandlerAdapter webSocketHandlerAdapter() {
        return new WebSocketHandlerAdapter();
    }

    @Bean("imGatewayWebSocketClient")
    @Primary
    ReactorNettyWebSocketClient imGatewayWebSocketClient() {
        return new ReactorNettyWebSocketClient();
    }

    private static boolean isWebSocketUpgrade(ServerWebExchange exchange) {
        if (exchange == null || exchange.getRequest() == null) {
            return false;
        }
        String upgrade = exchange.getRequest().getHeaders().getUpgrade();
        return upgrade != null && "websocket".equalsIgnoreCase(upgrade.trim());
    }
}
