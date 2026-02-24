package com.nowcoder.community.common.autoconfig;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.common.web.AuditLogFilter;
import com.nowcoder.community.common.web.GlobalExceptionHandler;
import com.nowcoder.community.common.net.ClientIpResolver;
import com.nowcoder.community.common.net.TrustedProxyProperties;
import com.nowcoder.community.common.web.ResultTraceIdAdvice;
import com.nowcoder.community.common.web.SecurityExceptionHandler;
import com.nowcoder.community.common.web.TraceIdFilter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.annotation.Value;

/**
 * servlet 专用自动装配：
 * - 只在 Servlet Web 应用启用（避免 gateway/reactive 加载 jakarta.servlet 相关类型）
 * - 提供统一的 Filter/异常处理器兜底，保证“即使未 scanBasePackages”也能生效
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(name = "jakarta.servlet.Filter")
public class ServletOnlyAutoConfiguration {

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
