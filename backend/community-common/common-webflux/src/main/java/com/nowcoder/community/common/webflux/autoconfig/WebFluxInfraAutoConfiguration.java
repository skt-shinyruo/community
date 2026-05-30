package com.nowcoder.community.common.webflux.autoconfig;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.common.json.JacksonJsonCodec;
import com.nowcoder.community.common.json.JsonCodec;
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
    public JsonCodec jsonCodec(ObjectMapper objectMapper) {
        return new JacksonJsonCodec(objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public SecurityExceptionHandler securityExceptionHandler(JsonCodec jsonCodec) {
        return new SecurityExceptionHandler(jsonCodec);
    }

    @Bean
    @ConditionalOnMissingBean
    public GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }
}
