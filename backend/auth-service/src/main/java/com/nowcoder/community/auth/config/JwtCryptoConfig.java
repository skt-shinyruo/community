package com.nowcoder.community.auth.config;

import com.nowcoder.community.infra.security.jwt.JwtProperties;
import com.nowcoder.community.infra.security.jwt.JwtSecretKeys;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;

@Configuration
public class JwtCryptoConfig {

    @Bean
    public JwtEncoder jwtEncoder(JwtProperties jwtProperties) {
        SecretKey secretKey = JwtSecretKeys.hmacSha256OrThrow(jwtProperties);
        return new NimbusJwtEncoder(new ImmutableSecret<>(secretKey));
    }
}
