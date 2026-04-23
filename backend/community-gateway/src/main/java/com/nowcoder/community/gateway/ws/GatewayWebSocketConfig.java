package com.nowcoder.community.gateway.ws;

import com.nowcoder.community.gateway.shard.DiscoveredWorkerDescriptorFactory;
import com.nowcoder.community.gateway.shard.WorkerRegistry;
import com.nowcoder.community.gateway.shard.WorkerDiscoveryProperties;
import com.nowcoder.community.gateway.security.GatewayCorsConfig;
import com.nowcoder.community.gateway.security.GatewayCorsProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.context.annotation.Primary;
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
@EnableConfigurationProperties({WsProxyProperties.class, WorkerDiscoveryProperties.class})
public class GatewayWebSocketConfig {

    @Bean
    HandlerMapping gatewayWebSocketMapping(
            ExternalImWebSocketHandler handler,
            GatewayCorsProperties corsProperties,
            @Value("${gateway.ws.proxy.path:/ws/im/workers/**}") String path
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
    @Primary
    ReactorNettyWebSocketClient gatewayWebSocketClient() {
        return new ReactorNettyWebSocketClient();
    }

    @Bean
    DiscoveredWorkerDescriptorFactory discoveredWorkerDescriptorFactory(WorkerDiscoveryProperties properties) {
        return new DiscoveredWorkerDescriptorFactory(properties);
    }

    @Bean
    WorkerRegistry workerRegistry(
            DiscoveryClient discoveryClient,
            WorkerDiscoveryProperties properties,
            DiscoveredWorkerDescriptorFactory factory
    ) {
        return new WorkerRegistry(discoveryClient, properties, factory);
    }

}
