package com.nowcoder.community.im.core.policy;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ImCorePolicyClientProperties.class)
public class PrivateMessagePolicyClientConfig {

    @Bean
    @LoadBalanced
    RestClient.Builder loadBalancedRestClientBuilder(ObjectProvider<RestClientCustomizer> customizers) {
        RestClient.Builder builder = RestClient.builder();
        customizers.orderedStream().forEach(customizer -> customizer.customize(builder));
        return builder;
    }

    @Bean("imPolicyRestClient")
    RestClient imPolicyRestClient(
            @LoadBalanced RestClient.Builder builder,
            ImCorePolicyClientProperties properties
    ) {
        return builder.clone()
                .baseUrl("http://" + properties.getCommunityServiceId())
                .requestFactory(requestFactory(properties))
                .build();
    }

    private SimpleClientHttpRequestFactory requestFactory(ImCorePolicyClientProperties properties) {
        int timeoutMs = Math.toIntExact(Math.min(Integer.MAX_VALUE, properties.normalizedRequestTimeout().toMillis()));
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeoutMs);
        factory.setReadTimeout(timeoutMs);
        return factory;
    }
}
