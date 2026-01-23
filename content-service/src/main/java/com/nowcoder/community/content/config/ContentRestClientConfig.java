// RestTemplate 客户端配置：用于调用 user-service/social-service 的内部接口，并透传 traceId 便于排障。
package com.nowcoder.community.content.config;

import com.nowcoder.community.common.web.TraceIdClientHttpRequestInterceptor;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class ContentRestClientConfig {

    @Bean
    @LoadBalanced
    public RestTemplate loadBalancedRestTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofMillis(300))
                .setReadTimeout(Duration.ofMillis(1000))
                .additionalInterceptors(new TraceIdClientHttpRequestInterceptor())
                .build();
    }
}

