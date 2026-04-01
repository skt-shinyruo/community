package com.nowcoder.community.common.security.jwt;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

public final class JwtCodecs {

    private JwtCodecs() {
    }

    public static NimbusJwtDecoder jwtDecoder(JwtProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(JwtSecretKeys.hmacSha256OrThrow(properties))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(resolvedIssuer(properties)));
        return decoder;
    }

    public static JwtEncoder jwtEncoder(JwtProperties properties) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(JwtSecretKeys.hmacSha256OrThrow(properties)));
    }

    public static String resolvedIssuer(JwtProperties properties) {
        String issuer = properties == null ? null : properties.getIssuer();
        if (issuer == null || issuer.isBlank()) {
            return "community-auth";
        }
        return issuer.trim();
    }
}
