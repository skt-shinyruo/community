package com.nowcoder.community.common.security.jwt;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class AccessTokenClaimsTest {

    @Test
    void securityVersionShouldAcceptOnlyPositiveIntegralClaims() {
        assertThat(AccessTokenClaims.securityVersion(jwt(7L))).hasValue(7L);
        assertThat(AccessTokenClaims.securityVersion(jwt(1))).hasValue(1L);

        assertThat(AccessTokenClaims.securityVersion(jwt(null))).isEmpty();
        assertThat(AccessTokenClaims.securityVersion(jwt(0L))).isEmpty();
        assertThat(AccessTokenClaims.securityVersion(jwt(-1L))).isEmpty();
        assertThat(AccessTokenClaims.securityVersion(jwt("7"))).isEmpty();
        assertThat(AccessTokenClaims.securityVersion(jwt(1.5D))).isEmpty();
    }

    private static Jwt jwt(Object securityVersion) {
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("00000000-0000-7000-8000-000000000001")
                .issuedAt(Instant.EPOCH)
                .expiresAt(Instant.EPOCH.plusSeconds(60));
        if (securityVersion != null) {
            builder.claim(AccessTokenClaims.SECURITY_VERSION, securityVersion);
        }
        return builder.build();
    }
}
