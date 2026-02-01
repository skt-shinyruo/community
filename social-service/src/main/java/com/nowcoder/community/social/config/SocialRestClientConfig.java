package com.nowcoder.community.social.config;

import com.nowcoder.community.common.web.TraceIdClientHttpRequestInterceptor;
import com.nowcoder.community.common.web.internalclient.InternalClientSupport;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class SocialRestClientConfig {

    @Bean
    @LoadBalanced
    public RestTemplate loadBalancedRestTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofMillis(300))
                .setReadTimeout(Duration.ofMillis(1000))
                .additionalInterceptors(new TraceIdClientHttpRequestInterceptor())
                .errorHandler(InternalClientSupport.passThroughResponseErrorHandler())
                .build();
    }
}

