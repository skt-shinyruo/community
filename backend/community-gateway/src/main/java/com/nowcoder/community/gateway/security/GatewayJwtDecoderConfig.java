package com.nowcoder.community.gateway.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

@Configuration(proxyBeanMethods = false)
public class GatewayJwtDecoderConfig {

    @Bean
    JwtDecoder gatewayJwtDecoder(
            @Value("${security.jwt.hmac-secret}") String secret,
            @Value("${security.jwt.issuer:community-auth}") String issuer
    ) {
        String value = secret == null ? "" : secret.trim();
        if (value.length() < 32) {
            throw new IllegalArgumentException("security.jwt.hmac-secret must be at least 32 characters");
        }
        String issuerValue = StringUtils.hasText(issuer) ? issuer.trim() : "community-auth";
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        SecretKey key = new SecretKeySpec(bytes, "HmacSHA256");
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuerValue));
        return decoder;
    }
}
