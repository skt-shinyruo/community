package com.nowcoder.community.infra.security.autoconfig;

import com.nowcoder.community.infra.security.jwt.JwtProperties;
import com.nowcoder.community.infra.security.metrics.MetricsBasicAuthProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@AutoConfiguration
@EnableConfigurationProperties({
        JwtProperties.class,
        MetricsBasicAuthProperties.class
})
public class SecurityInfraAutoConfiguration {
}

