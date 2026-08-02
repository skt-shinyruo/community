package com.nowcoder.community.common.security.autoconfig;

import com.nowcoder.community.common.security.jwt.JwtCodecs;
import com.nowcoder.community.common.security.jwt.JwtProperties;
import com.nowcoder.community.common.security.jwt.JwtRsaKeys;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.JwtDecoder;

@AutoConfiguration
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityCommonAutoConfiguration {

    @Bean
    JwtConfigurationValidator jwtConfigurationValidator(JwtProperties jwtProperties) {
        return new JwtConfigurationValidator(jwtProperties);
    }

    @Bean
    @ConditionalOnMissingBean(JwtDecoder.class)
    JwtDecoder jwtDecoder(JwtProperties jwtProperties) {
        return JwtCodecs.accessTokenDecoder(jwtProperties);
    }

    static final class JwtConfigurationValidator {

        JwtConfigurationValidator(JwtProperties jwtProperties) {
            JwtRsaKeys.accessPublicKeyOrThrow(jwtProperties);
            JwtCodecs.resolvedIssuer(jwtProperties);
            JwtCodecs.resolvedAccessTokenAudience(jwtProperties);
        }
    }
}
