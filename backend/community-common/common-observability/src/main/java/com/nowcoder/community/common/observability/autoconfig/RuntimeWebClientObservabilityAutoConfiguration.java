package com.nowcoder.community.common.observability.autoconfig;

import com.nowcoder.community.common.observability.http.HttpClientRuntimeLogger;
import com.nowcoder.community.common.observability.http.RuntimeWebClientCustomizer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.reactive.function.client.WebClientCustomizer;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(after = RuntimeObservabilityAutoConfiguration.class)
@ConditionalOnClass(name = "org.springframework.web.reactive.function.client.WebClient")
public class RuntimeWebClientObservabilityAutoConfiguration {

    private static final String PREFIX = "community.observability.runtime-logging";

    @Bean
    @ConditionalOnMissingBean(RuntimeWebClientCustomizer.class)
    @ConditionalOnBean(HttpClientRuntimeLogger.class)
    @ConditionalOnProperty(prefix = PREFIX + ".http-client", name = "enabled", havingValue = "true", matchIfMissing = true)
    public WebClientCustomizer runtimeWebClientCustomizer(HttpClientRuntimeLogger logger) {
        return new RuntimeWebClientCustomizer(logger);
    }
}
