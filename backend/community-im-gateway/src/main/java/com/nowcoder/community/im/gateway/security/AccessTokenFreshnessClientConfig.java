package com.nowcoder.community.im.gateway.security;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webclient.WebClientCustomizer;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AccessTokenFreshnessProperties.class)
public class AccessTokenFreshnessClientConfig {

    @Bean
    @LoadBalanced
    WebClient.Builder imGatewayLoadBalancedWebClientBuilder(
            ObjectProvider<WebClientCustomizer> customizers
    ) {
        WebClient.Builder builder = WebClient.builder();
        customizers.orderedStream().forEach(customizer -> customizer.customize(builder));
        return builder;
    }

    @Bean("imAccessTokenFreshnessWebClient")
    WebClient imAccessTokenFreshnessWebClient(
            @LoadBalanced WebClient.Builder builder,
            AccessTokenFreshnessProperties properties
    ) {
        return builder.clone()
                .baseUrl("http://" + properties.normalizedCommunityServiceId())
                .build();
    }
}
