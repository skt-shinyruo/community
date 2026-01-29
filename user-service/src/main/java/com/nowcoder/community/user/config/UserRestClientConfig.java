package com.nowcoder.community.user.config;

import com.nowcoder.community.common.web.internalclient.InternalClientSupport;
import com.nowcoder.community.common.web.TraceIdClientHttpRequestInterceptor;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class UserRestClientConfig {

    @Bean
    @LoadBalanced
    public RestTemplate loadBalancedRestTemplate(RestTemplateBuilder builder, SocialServiceClientProperties properties) {
        return builder
                .setConnectTimeout(properties.getConnectTimeout())
                .setReadTimeout(properties.getReadTimeout())
                .additionalInterceptors(new TraceIdClientHttpRequestInterceptor())
                .errorHandler(InternalClientSupport.passThroughResponseErrorHandler())
                .build();
    }
}
