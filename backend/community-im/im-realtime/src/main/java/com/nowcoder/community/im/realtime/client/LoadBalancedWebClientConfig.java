package com.nowcoder.community.im.realtime.client;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;
import com.nowcoder.community.im.realtime.session.ImSessionProperties;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({ImServiceClientProperties.class, ImSessionProperties.class})
public class LoadBalancedWebClientConfig {

    @Bean
    @LoadBalanced
    WebClient.Builder loadBalancedWebClientBuilder() {
        return WebClient.builder();
    }

    @Bean("communityGovernanceWebClient")
    WebClient communityGovernanceWebClient(
            @LoadBalanced WebClient.Builder builder,
            ImServiceClientProperties properties
    ) {
        return builder.clone()
                .baseUrl("http://" + properties.getCommunityServiceId())
                .build();
    }

    @Bean("imCoreWebClient")
    WebClient imCoreWebClient(
            @LoadBalanced WebClient.Builder builder,
            ImServiceClientProperties properties
    ) {
        return builder.clone()
                .baseUrl("http://" + properties.getImCoreServiceId())
                .build();
    }

    @Bean("membershipSnapshotWebClient")
    WebClient membershipSnapshotWebClient(
            @LoadBalanced WebClient.Builder builder,
            ImServiceClientProperties properties
    ) {
        return builder.clone()
                .baseUrl("http://" + properties.getMembershipSnapshotServiceId())
                .build();
    }

    @Bean("policySnapshotWebClient")
    WebClient policySnapshotWebClient(
            @LoadBalanced WebClient.Builder builder,
            ImServiceClientProperties properties
    ) {
        return builder.clone()
                .baseUrl("http://" + properties.getPolicySnapshotServiceId())
                .build();
    }
}
