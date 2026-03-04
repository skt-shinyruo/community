package com.nowcoder.community.infra.web.autoconfig;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.infra.web.reactive.ReactiveSecurityExceptionHandler;
import com.nowcoder.community.infra.web.reactive.TraceIdWebFilter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
public class ReactiveWebInfraAutoConfiguration {

    @Bean
    @ConditionalOnClass(name = "org.springframework.web.server.WebFilter")
    @ConditionalOnMissingBean
    public TraceIdWebFilter traceIdWebFilter() {
        return new TraceIdWebFilter();
    }

    @Bean
    @ConditionalOnClass(name = "org.springframework.security.web.server.ServerAuthenticationEntryPoint")
    @ConditionalOnMissingBean
    public ReactiveSecurityExceptionHandler reactiveSecurityExceptionHandler(ObjectMapper objectMapper) {
        return new ReactiveSecurityExceptionHandler(objectMapper);
    }
}

