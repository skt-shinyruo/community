package com.nowcoder.community.infra.security.autoconfig;

import com.nowcoder.community.infra.security.metrics.MetricsBasicAuthProperties;
import com.nowcoder.community.infra.security.origin.OriginGuardProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@AutoConfiguration
@EnableConfigurationProperties({
        MetricsBasicAuthProperties.class,
        OriginGuardProperties.class
})
public class SecurityInfraAutoConfiguration {
}
