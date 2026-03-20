package com.nowcoder.community.gateway.ws;

import com.nowcoder.community.gateway.shard.ConsistentHashShardRouter;
import com.nowcoder.community.gateway.shard.ShardRouter;
import com.nowcoder.community.gateway.shard.WorkerRegistry;
import com.nowcoder.community.gateway.shard.WorkerRegistryProperties;
import com.nowcoder.community.gateway.security.GatewayCorsConfig;
import com.nowcoder.community.gateway.security.GatewayCorsProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;

import java.util.Map;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({WsProxyProperties.class, WorkerRegistryProperties.class})
public class GatewayWebSocketConfig {

    @Bean
    HandlerMapping gatewayWebSocketMapping(
            ExternalImWebSocketHandler handler,
            GatewayCorsProperties corsProperties,
            @Value("${gateway.ws.proxy.path:/ws/im}") String path
    ) {
        SimpleUrlHandlerMapping mapping = new SimpleUrlHandlerMapping();
        mapping.setOrder(-1);
        mapping.setUrlMap(Map.of(String.valueOf(path), handler));
        CorsConfiguration wsCors = GatewayCorsConfig.buildCorsConfiguration(corsProperties);
        mapping.setCorsConfigurations(Map.of(String.valueOf(path), wsCors));
        return mapping;
    }

    @Bean
    WebSocketHandlerAdapter webSocketHandlerAdapter() {
        return new WebSocketHandlerAdapter();
    }

    @Bean
    ReactorNettyWebSocketClient gatewayWebSocketClient() {
        return new ReactorNettyWebSocketClient();
    }

    @Bean
    WorkerRegistry workerRegistry(WorkerRegistryProperties properties) {
        return new WorkerRegistry(properties);
    }

    @Bean
    ShardRouter shardRouter(WorkerRegistry registry) {
        return new ConsistentHashShardRouter(registry);
    }
}
