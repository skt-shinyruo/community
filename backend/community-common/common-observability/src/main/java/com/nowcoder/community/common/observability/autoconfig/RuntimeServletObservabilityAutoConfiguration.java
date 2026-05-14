package com.nowcoder.community.common.observability.autoconfig;

import com.nowcoder.community.common.observability.http.ServletAccessRuntimeLogFilter;
import com.nowcoder.community.common.observability.logging.RuntimeLogWriter;
import com.nowcoder.community.common.observability.logging.RuntimeLoggingProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(after = RuntimeObservabilityAutoConfiguration.class)
@ConditionalOnClass(name = "jakarta.servlet.Filter")
public class RuntimeServletObservabilityAutoConfiguration {

    private static final String PREFIX = "community.observability.runtime-logging";

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(RuntimeLogWriter.class)
    @ConditionalOnProperty(prefix = PREFIX + ".http", name = "access-log-enabled", havingValue = "true", matchIfMissing = true)
    public ServletAccessRuntimeLogFilter servletAccessRuntimeLogFilter(
            RuntimeLogWriter logWriter,
            RuntimeLoggingProperties properties
    ) {
        return new ServletAccessRuntimeLogFilter(logWriter, properties);
    }

    @Bean
    @ConditionalOnMissingBean(name = "runtimeServletAccessLogFilterRegistration")
    @ConditionalOnBean(ServletAccessRuntimeLogFilter.class)
    public FilterRegistrationBean<ServletAccessRuntimeLogFilter> runtimeServletAccessLogFilterRegistration(
            ServletAccessRuntimeLogFilter filter
    ) {
        FilterRegistrationBean<ServletAccessRuntimeLogFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setName("runtimeServletAccessLogFilter");
        registration.setOrder(Integer.MAX_VALUE - 20);
        return registration;
    }
}
