package com.nowcoder.community.im.realtime.client;

import com.nowcoder.community.common.security.jwt.JwtProperties;
import com.nowcoder.community.im.realtime.session.ImSessionProperties;
import com.nowcoder.community.im.ticket.ImSessionTicketProperties;
import com.nowcoder.community.im.ticket.SessionTicketCodec;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webclient.WebClientCustomizer;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({
        ImServiceClientProperties.class,
        ImSessionProperties.class,
        ImSessionTicketProperties.class
})
public class LoadBalancedWebClientConfig {

    @Bean
    SessionTicketCodec sessionTicketCodec(
            JwtProperties accessProperties,
            ImSessionTicketProperties ticketProperties
    ) {
        return new SessionTicketCodec(
                ticketProperties,
                ticketProperties.secretKeyOrThrow(accessProperties.getServiceHmacSecret())
        );
    }

    @Bean
    @LoadBalanced
    WebClient.Builder loadBalancedWebClientBuilder(ObjectProvider<WebClientCustomizer> customizers) {
        WebClient.Builder builder = WebClient.builder();
        customizers.orderedStream().forEach(customizer -> customizer.customize(builder));
        return builder;
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
