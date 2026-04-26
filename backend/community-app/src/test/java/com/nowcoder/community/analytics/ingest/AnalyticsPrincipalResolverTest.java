package com.nowcoder.community.analytics.ingest;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AnalyticsPrincipalResolverTest {

    @Test
    void shouldResolveUuidSubjectFromJwtPrincipal() {
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(userId.toString())
                .issuedAt(Instant.parse("2026-01-01T00:00:00Z"))
                .expiresAt(Instant.parse("2026-01-01T01:00:00Z"))
                .build();
        TestingAuthenticationToken authentication = new TestingAuthenticationToken(jwt, null);
        authentication.setAuthenticated(true);

        assertThat(new AnalyticsPrincipalResolver().resolveUserUuid(authentication)).isEqualTo(userId);
    }

    @Test
    void shouldIgnoreAnonymousOrInvalidPrincipal() {
        AnalyticsPrincipalResolver resolver = new AnalyticsPrincipalResolver();

        assertThat(resolver.resolveUserUuid(null)).isNull();
        assertThat(resolver.resolveUserUuid(new TestingAuthenticationToken("anonymousUser", null))).isNull();
    }
}
