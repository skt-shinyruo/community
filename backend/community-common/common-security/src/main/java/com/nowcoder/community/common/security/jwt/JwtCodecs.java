package com.nowcoder.community.common.security.jwt;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
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
        OAuth2TokenValidator<Jwt> issuer = JwtValidators.createDefaultWithIssuer(resolvedIssuer(properties));
        OAuth2TokenValidator<Jwt> accessType = jwt -> "im-session-ticket".equals(jwt.getClaimAsString("typ"))
                ? OAuth2TokenValidatorResult.failure(new OAuth2Error(
                        "invalid_token", "IM session ticket is not an access token", null))
                : OAuth2TokenValidatorResult.success();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(issuer, accessType));
        return decoder;
    }

    public static JwtEncoder jwtEncoder(JwtProperties properties) {
        resolvedIssuer(properties);
        return new NimbusJwtEncoder(new ImmutableSecret<>(JwtSecretKeys.hmacSha256OrThrow(properties)));
    }

    public static String resolvedIssuer(JwtProperties properties) {
        String issuer = properties == null ? null : properties.getIssuer();
        if (issuer == null || issuer.isBlank()) {
            throw new IllegalArgumentException("security.jwt.issuer is required");
        }
        return issuer.trim();
    }
}
