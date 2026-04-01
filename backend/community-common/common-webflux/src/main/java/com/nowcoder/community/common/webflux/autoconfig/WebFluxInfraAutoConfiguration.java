package com.nowcoder.community.common.webflux.autoconfig;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.common.webflux.GlobalExceptionHandler;
import com.nowcoder.community.common.webflux.SecurityExceptionHandler;
import com.nowcoder.community.common.webflux.TraceIdWebFilter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
@ConditionalOnClass(name = "org.springframework.web.server.WebFilter")
public class WebFluxInfraAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public TraceIdWebFilter traceIdWebFilter() {
        return new TraceIdWebFilter();
    }

    @Bean
    @ConditionalOnMissingBean
    public SecurityExceptionHandler securityExceptionHandler(ObjectMapper objectMapper) {
        return new SecurityExceptionHandler(objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }
}
