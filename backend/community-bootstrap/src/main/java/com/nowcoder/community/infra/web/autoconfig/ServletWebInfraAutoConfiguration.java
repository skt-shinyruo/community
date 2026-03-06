package com.nowcoder.community.infra.web.autoconfig;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.infra.web.AuditLogFilter;
import com.nowcoder.community.infra.web.GlobalExceptionHandler;
import com.nowcoder.community.infra.web.ResultTraceIdAdvice;
import com.nowcoder.community.infra.web.SecurityExceptionHandler;
import com.nowcoder.community.infra.web.TraceIdFilter;
import com.nowcoder.community.infra.web.net.ClientIpResolver;
import com.nowcoder.community.infra.web.net.TrustedProxyProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(name = "jakarta.servlet.Filter")
public class ServletWebInfraAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public TraceIdFilter traceIdFilter() {
        return new TraceIdFilter();
    }

    @Bean
    @ConditionalOnMissingBean
    public AuditLogFilter auditLogFilter(@Value("${spring.application.name:unknown}") String serviceName) {
        return new AuditLogFilter(serviceName);
    }

    @Bean
    @ConditionalOnMissingBean
    public GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }

    @Bean
    @ConditionalOnMissingBean
    public ResultTraceIdAdvice resultTraceIdAdvice() {
        return new ResultTraceIdAdvice();
    }

    @Bean
    @ConditionalOnMissingBean
    public SecurityExceptionHandler securityExceptionHandler(ObjectMapper objectMapper) {
        return new SecurityExceptionHandler(objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public ClientIpResolver clientIpResolver(TrustedProxyProperties trustedProxyProperties) {
        return new ClientIpResolver(trustedProxyProperties);
    }
}

