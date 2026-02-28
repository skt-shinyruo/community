package com.nowcoder.community.platform.autoconfig;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.platform.web.reactive.ReactiveSecurityExceptionHandler;
import com.nowcoder.community.platform.web.reactive.TraceIdWebFilter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

/**
 * reactive 专用自动装配：
 * - 为 gateway（以及未来 reactive 服务）提供跨服务一致的“协议级”横切能力
 * - 避免每个 reactive 应用重复实现 trace 注入/安全异常回填等基础能力
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
public class ReactiveOnlyAutoConfiguration {

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
