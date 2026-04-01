package com.nowcoder.community.common.security.autoconfig;

import com.nowcoder.community.common.security.jwt.JwtCodecs;
import com.nowcoder.community.common.security.jwt.JwtProperties;
import com.nowcoder.community.common.security.jwt.JwtSecretKeys;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityCommonAutoConfiguration {

    @Bean
    JwtConfigurationValidator jwtConfigurationValidator(JwtProperties jwtProperties) {
        return new JwtConfigurationValidator(jwtProperties);
    }

    static final class JwtConfigurationValidator {

        JwtConfigurationValidator(JwtProperties jwtProperties) {
            JwtSecretKeys.hmacSha256OrThrow(jwtProperties);
            JwtCodecs.resolvedIssuer(jwtProperties);
        }
    }
}
