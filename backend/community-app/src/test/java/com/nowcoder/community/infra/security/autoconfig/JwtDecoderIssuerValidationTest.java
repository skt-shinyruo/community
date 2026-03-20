package com.nowcoder.community.infra.security.autoconfig;

import com.nowcoder.community.infra.security.jwt.JwtProperties;
import com.nowcoder.community.infra.security.jwt.JwtSecretKeys;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtDecoderIssuerValidationTest {

    @Test
    void jwtDecoderShouldRejectWrongIssuer() {
        JwtProperties properties = new JwtProperties();
        properties.setHmacSecret("0123456789abcdef0123456789abcdef");
        properties.setIssuer("community-auth");

        JwtDecoder decoder = new ServletInfraSecurityConfig().jwtDecoder(properties);
        JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(JwtSecretKeys.hmacSha256OrThrow(properties)));

        Instant now = Instant.now();
        String token = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(),
                JwtClaimsSet.builder()
                        .issuer("other-issuer")
                        .issuedAt(now)
                        .expiresAt(now.plusSeconds(60))
                        .subject("7")
                        .build()
        )).getTokenValue();

        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtValidationException.class);
    }
}
