package com.nowcoder.community.search.config;

import com.nowcoder.community.common.web.TraceIdClientHttpRequestInterceptor;
import com.nowcoder.community.common.web.internalclient.InternalClientSupport;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * search-service 内部 HTTP 客户端配置。
 */
@Configuration
public class SearchRestClientConfig {

    @Bean
    @LoadBalanced
    public RestTemplate loadBalancedRestTemplate(RestTemplateBuilder builder, ContentServiceClientProperties properties) {
        return builder
                .setConnectTimeout(properties.getConnectTimeout())
                .setReadTimeout(properties.getReadTimeout())
                .additionalInterceptors(new TraceIdClientHttpRequestInterceptor())
                .errorHandler(InternalClientSupport.passThroughResponseErrorHandler())
                .build();
    }
}
