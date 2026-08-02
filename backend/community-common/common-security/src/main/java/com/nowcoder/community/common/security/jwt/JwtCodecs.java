package com.nowcoder.community.common.security.jwt;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.DefaultJOSEObjectTypeVerifier;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

public final class JwtCodecs {

    public static final String ACCESS_TOKEN_TYPE = "at+jwt";
    public static final String SERVICE_TOKEN_TYPE = "service+jwt";

    private JwtCodecs() {
    }

    public static NimbusJwtDecoder accessTokenDecoder(JwtProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withPublicKey(JwtRsaKeys.accessPublicKeyOrThrow(properties))
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                .jwtProcessorCustomizer(processor -> processor.setJWSTypeVerifier(
                        new DefaultJOSEObjectTypeVerifier<>(new JOSEObjectType(ACCESS_TOKEN_TYPE))
                ))
                .build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(resolvedIssuer(properties)),
                tokenTypeValidator(ACCESS_TOKEN_TYPE),
                audienceValidator(resolvedAccessTokenAudience(properties))
        ));
        return decoder;
    }

    public static NimbusJwtDecoder serviceTokenDecoder(
            JwtProperties properties,
            String expectedIssuer,
            String expectedAudience
    ) {
        String issuer = requireText("service token issuer", expectedIssuer);
        String audience = requireText("service token audience", expectedAudience);
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withSecretKey(JwtSecretKeys.serviceHmacSha256OrThrow(properties))
                .macAlgorithm(MacAlgorithm.HS256)
                .jwtProcessorCustomizer(processor -> processor.setJWSTypeVerifier(
                        new DefaultJOSEObjectTypeVerifier<>(new JOSEObjectType(SERVICE_TOKEN_TYPE))
                ))
                .build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuer),
                tokenTypeValidator(SERVICE_TOKEN_TYPE),
                audienceValidator(audience)
        ));
        return decoder;
    }

    public static JwtEncoder accessTokenEncoder(JwtProperties properties) {
        resolvedIssuer(properties);
        resolvedAccessTokenAudience(properties);
        RSAPublicKey publicKey = JwtRsaKeys.accessPublicKeyOrThrow(properties);
        RSAPrivateKey privateKey = JwtRsaKeys.accessPrivateKeyOrThrow(properties);
        JwtRsaKeys.requireMatchingAccessKeyPair(publicKey, privateKey);
        RSAKey rsaKey = new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .algorithm(JWSAlgorithm.RS256)
                .build();
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(rsaKey)));
    }

    public static JwtEncoder serviceTokenEncoder(JwtProperties properties) {
        resolvedIssuer(properties);
        return new NimbusJwtEncoder(new ImmutableSecret<>(JwtSecretKeys.serviceHmacSha256OrThrow(properties)));
    }

    public static String resolvedIssuer(JwtProperties properties) {
        String issuer = properties == null ? null : properties.getIssuer();
        if (issuer == null || issuer.isBlank()) {
            throw new IllegalArgumentException("security.jwt.issuer is required");
        }
        return issuer.trim();
    }

    public static String resolvedAccessTokenAudience(JwtProperties properties) {
        String audience = properties == null ? null : properties.getAccessTokenAudience();
        if (audience == null || audience.isBlank()) {
            throw new IllegalArgumentException("security.jwt.access-token-audience is required");
        }
        return audience.trim();
    }

    private static OAuth2TokenValidator<Jwt> tokenTypeValidator(String expectedType) {
        return jwt -> expectedType.equals(jwt.getHeaders().get("typ"))
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error(
                        "invalid_token", "The required token type is missing or invalid", null));
    }

    private static OAuth2TokenValidator<Jwt> audienceValidator(String expectedAudience) {
        return jwt -> jwt.getAudience() != null && jwt.getAudience().contains(expectedAudience)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error(
                        "invalid_token", "The required audience is missing", null));
    }

    private static String requireText(String name, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }
}
