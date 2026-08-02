package com.nowcoder.community.common.security.jwt;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.jwt.JwsHeader;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtCodecsTest {

    private static final String SERVICE_SECRET = "plan-test-service-jwt-secret-please-change-123456";
    private static final KeyPair ACCESS_KEY_PAIR = rsaKeyPair();

    @Test
    void accessTokenDecoder_shouldRejectInvalidPublicKey() {
        JwtProperties properties = accessProperties();
        properties.setAccessPublicKey("not-a-key");

        assertThatThrownBy(() -> JwtCodecs.accessTokenDecoder(properties))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("security.jwt.access-public-key");
    }

    @Test
    void accessTokenEncoder_shouldRejectMissingPrivateKey() {
        JwtProperties properties = accessProperties();
        properties.setAccessPrivateKey(null);

        assertThatThrownBy(() -> JwtCodecs.accessTokenEncoder(properties))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("security.jwt.access-private-key");
    }

    @Test
    void accessTokenEncoderAndDecoder_shouldUseRs256AndRequiredHeaderClaims() {
        JwtProperties properties = accessProperties();
        String token = accessToken(properties, "community-auth", "community-api", JwtCodecs.ACCESS_TOKEN_TYPE);

        Jwt decoded = JwtCodecs.accessTokenDecoder(publicOnly(properties)).decode(token);

        assertThat(decoded.getSubject()).isEqualTo("123");
        assertThat(decoded.getClaimAsString("iss")).isEqualTo("community-auth");
        assertThat(decoded.getAudience()).containsExactly("community-api");
        assertThat(decoded.getHeaders())
                .containsEntry("alg", "RS256")
                .containsEntry("typ", "at+jwt");
        assertThat(decoded.getClaims()).doesNotContainKey("typ");
    }

    @Test
    void accessTokenDecoder_shouldRejectWrongIssuerAudienceOrType() {
        JwtProperties properties = accessProperties();
        JwtDecoder decoder = JwtCodecs.accessTokenDecoder(properties);

        assertThatThrownBy(() -> decoder.decode(accessToken(
                properties, "wrong-issuer", "community-api", JwtCodecs.ACCESS_TOKEN_TYPE)))
                .isInstanceOf(JwtValidationException.class);
        assertThatThrownBy(() -> decoder.decode(accessToken(
                properties, "community-auth", "other-api", JwtCodecs.ACCESS_TOKEN_TYPE)))
                .isInstanceOf(JwtValidationException.class);
        assertThatThrownBy(() -> decoder.decode(accessToken(
                properties, "community-auth", "community-api", null)))
                .isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> decoder.decode(accessToken(
                properties, "community-auth", "community-api", JwtCodecs.SERVICE_TOKEN_TYPE)))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void serviceTokenEncoderAndDecoder_shouldUseIndependentHs256TrustDomain() {
        JwtProperties properties = serviceProperties();
        JwtClaimsSet claims = standardClaims("community-auth", "community-oss")
                .claim("scope", "oss.internal")
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256)
                .type(JwtCodecs.SERVICE_TOKEN_TYPE)
                .build();
        String token = JwtCodecs.serviceTokenEncoder(properties)
                .encode(JwtEncoderParameters.from(header, claims))
                .getTokenValue();

        Jwt decoded = JwtCodecs.serviceTokenDecoder(properties, "community-auth", "community-oss").decode(token);

        assertThat(decoded.getHeaders())
                .containsEntry("alg", "HS256")
                .containsEntry("typ", "service+jwt");
        assertThat(decoded.getAudience()).containsExactly("community-oss");
        assertThat(decoded.getClaimAsString("scope")).isEqualTo("oss.internal");
    }

    @Test
    void serviceTokenDecoder_shouldRejectWrongIssuerAudienceOrType() {
        JwtProperties properties = serviceProperties();
        JwtDecoder decoder = JwtCodecs.serviceTokenDecoder(properties, "community-auth", "community-app");

        assertThatThrownBy(() -> decoder.decode(serviceToken(
                properties, "wrong-issuer", "community-app", JwtCodecs.SERVICE_TOKEN_TYPE)))
                .isInstanceOf(JwtValidationException.class);
        assertThatThrownBy(() -> decoder.decode(serviceToken(
                properties, "community-auth", "im-core", JwtCodecs.SERVICE_TOKEN_TYPE)))
                .isInstanceOf(JwtValidationException.class);
        assertThatThrownBy(() -> decoder.decode(serviceToken(
                properties, "community-auth", "community-app", null)))
                .isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> decoder.decode(serviceToken(
                properties, "community-auth", "community-app", JwtCodecs.ACCESS_TOKEN_TYPE)))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void serviceSecret_shouldRejectKnownPlaceholderAndShortValues() {
        JwtProperties placeholder = serviceProperties();
        placeholder.setServiceHmacSecret("dev-secret-please-change-at-least-32bytes");
        JwtProperties shortSecret = serviceProperties();
        shortSecret.setServiceHmacSecret("too-short");

        assertThatThrownBy(() -> JwtSecretKeys.serviceHmacSha256OrThrow(placeholder))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("security.jwt.service-hmac-secret");
        assertThatThrownBy(() -> JwtSecretKeys.serviceHmacSha256OrThrow(shortSecret))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("security.jwt.service-hmac-secret");
    }

    @Test
    void resolvedIssuerAndAudience_shouldTrimAndRequireValues() {
        JwtProperties properties = accessProperties();
        properties.setIssuer("  issuer-a  ");
        properties.setAccessTokenAudience("  audience-a  ");

        assertThat(JwtCodecs.resolvedIssuer(properties)).isEqualTo("issuer-a");
        assertThat(JwtCodecs.resolvedAccessTokenAudience(properties)).isEqualTo("audience-a");

        properties.setIssuer(" ");
        assertThatThrownBy(() -> JwtCodecs.resolvedIssuer(properties))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("security.jwt.issuer");
        properties.setIssuer("issuer-a");
        properties.setAccessTokenAudience(" ");
        assertThatThrownBy(() -> JwtCodecs.resolvedAccessTokenAudience(properties))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("security.jwt.access-token-audience");
    }

    private static String accessToken(JwtProperties properties, String issuer, String audience, String type) {
        JwsHeader.Builder headers = JwsHeader.with(SignatureAlgorithm.RS256);
        if (type != null) {
            headers.type(type);
        }
        JwtClaimsSet claims = standardClaims(issuer, audience).build();
        return JwtCodecs.accessTokenEncoder(properties)
                .encode(JwtEncoderParameters.from(headers.build(), claims))
                .getTokenValue();
    }

    private static String serviceToken(JwtProperties properties, String issuer, String audience, String type) {
        JwsHeader.Builder headers = JwsHeader.with(MacAlgorithm.HS256);
        if (type != null) {
            headers.type(type);
        }
        return JwtCodecs.serviceTokenEncoder(properties)
                .encode(JwtEncoderParameters.from(headers.build(), standardClaims(issuer, audience).build()))
                .getTokenValue();
    }

    private static JwtClaimsSet.Builder standardClaims(String issuer, String audience) {
        return JwtClaimsSet.builder()
                .subject("123")
                .issuer(issuer)
                .audience(List.of(audience))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300));
    }

    private static JwtProperties accessProperties() {
        JwtProperties properties = new JwtProperties();
        properties.setAccessPublicKey(Base64.getEncoder().encodeToString(ACCESS_KEY_PAIR.getPublic().getEncoded()));
        properties.setAccessPrivateKey(Base64.getEncoder().encodeToString(ACCESS_KEY_PAIR.getPrivate().getEncoded()));
        properties.setIssuer("community-auth");
        properties.setAccessTokenAudience("community-api");
        return properties;
    }

    private static JwtProperties publicOnly(JwtProperties source) {
        JwtProperties properties = new JwtProperties();
        properties.setAccessPublicKey(source.getAccessPublicKey());
        properties.setIssuer(source.getIssuer());
        properties.setAccessTokenAudience(source.getAccessTokenAudience());
        return properties;
    }

    private static JwtProperties serviceProperties() {
        JwtProperties properties = new JwtProperties();
        properties.setServiceHmacSecret(SERVICE_SECRET);
        properties.setIssuer("community-auth");
        return properties;
    }

    private static KeyPair rsaKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
