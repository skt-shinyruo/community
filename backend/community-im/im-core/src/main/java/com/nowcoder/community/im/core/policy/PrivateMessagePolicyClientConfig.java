package com.nowcoder.community.im.core.policy;

import com.nowcoder.community.im.core.security.AccessTokenFreshnessProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({ImCorePolicyClientProperties.class, AccessTokenFreshnessProperties.class})
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

    @Bean("imAccessTokenFreshnessRestClient")
    RestClient imAccessTokenFreshnessRestClient(
            @LoadBalanced RestClient.Builder builder,
            AccessTokenFreshnessProperties properties
    ) {
        return builder.clone()
                .baseUrl("http://" + properties.normalizedCommunityServiceId())
                .requestFactory(requestFactory(properties.normalizedRequestTimeout()))
                .build();
    }

    private SimpleClientHttpRequestFactory requestFactory(ImCorePolicyClientProperties properties) {
        return requestFactory(properties.normalizedRequestTimeout());
    }

    private SimpleClientHttpRequestFactory requestFactory(java.time.Duration timeout) {
        int timeoutMs = Math.toIntExact(Math.min(Integer.MAX_VALUE, Math.max(1L, timeout.toMillis())));
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeoutMs);
        factory.setReadTimeout(timeoutMs);
        return factory;
    }
}
