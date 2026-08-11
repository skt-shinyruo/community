package com.nowcoder.community.common.observability.autoconfig;

import com.nowcoder.community.common.observability.http.HttpClientRuntimeLogger;
import com.nowcoder.community.common.observability.http.RuntimeRestClientCustomizer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(after = RuntimeObservabilityAutoConfiguration.class)
@ConditionalOnClass(name = {
        "org.springframework.web.client.RestClient",
        "org.springframework.boot.restclient.RestClientCustomizer"
})
public class RuntimeRestClientObservabilityAutoConfiguration {

    private static final String PREFIX = "community.observability.runtime-logging";

    @Bean
    @ConditionalOnMissingBean(RuntimeRestClientCustomizer.class)
    @ConditionalOnBean(HttpClientRuntimeLogger.class)
    @ConditionalOnProperty(prefix = PREFIX + ".http-client", name = "enabled", havingValue = "true", matchIfMissing = true)
    public RestClientCustomizer runtimeRestClientCustomizer(HttpClientRuntimeLogger logger) {
        return new RuntimeRestClientCustomizer(logger);
    }
}
