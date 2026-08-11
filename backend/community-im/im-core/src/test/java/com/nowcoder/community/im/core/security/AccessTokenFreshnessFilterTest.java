package com.nowcoder.community.im.core.security;

import com.nowcoder.community.common.security.jwt.JwtCodecs;
import com.nowcoder.community.im.core.application.AccessTokenFreshnessApplicationService;
import com.nowcoder.community.im.core.application.AccessTokenFreshnessApplicationService.Decision;
import com.nowcoder.community.im.core.application.AccessTokenFreshnessApplicationService.OwnerVerifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class AccessTokenFreshnessFilterTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void missingSecurityVersionShouldFailBeforeCallingTheOwner() throws Exception {
        StubVerifier verifier = new StubVerifier();
        setJwt(jwt(null));
        MockHttpServletResponse response = invoke(filter(verifier));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(verifier.invocations()).isZero();
    }

    @Test
    void ownerDecisionShouldMapToFailClosedHttpStatus() throws Exception {
        assertDecision(Decision.FRESH, 200, 1);
        assertDecision(Decision.STALE, 401, 0);
        assertDecision(Decision.DENIED, 403, 0);
        assertDecision(Decision.UNAVAILABLE, 503, 0);
    }

    @Test
    void unexpectedOwnerFailureShouldFailClosed() throws Exception {
        OwnerVerifier verifier = accessToken -> {
            throw new IllegalStateException("owner unavailable");
        };
        setJwt(jwt(7L));

        assertThat(invoke(filter(verifier)).getStatus()).isEqualTo(503);
    }

    private static void assertDecision(
            Decision decision,
            int expectedStatus,
            int expectedChainInvocations
    ) throws Exception {
        StubVerifier verifier = new StubVerifier();
        verifier.decision(decision);
        setJwt(jwt(7L));
        AtomicInteger chainInvocations = new AtomicInteger();
        MockHttpServletResponse response = invoke(filter(verifier), chainInvocations);

        assertThat(response.getStatus()).isEqualTo(expectedStatus);
        assertThat(chainInvocations).hasValue(expectedChainInvocations);
    }

    private static MockHttpServletResponse invoke(AccessTokenFreshnessFilter filter) throws Exception {
        return invoke(filter, new AtomicInteger());
    }

    private static MockHttpServletResponse invoke(
            AccessTokenFreshnessFilter filter,
            AtomicInteger chainInvocations
    ) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/im/unread/summary");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> chainInvocations.incrementAndGet());
        return response;
    }

    private static void setJwt(Jwt jwt) {
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    private static AccessTokenFreshnessFilter filter(OwnerVerifier verifier) {
        return new AccessTokenFreshnessFilter(new AccessTokenFreshnessApplicationService(verifier));
    }

    private static Jwt jwt(Long securityVersion) {
        Jwt.Builder builder = Jwt.withTokenValue("access-token")
                .header("alg", "RS256")
                .header("typ", JwtCodecs.ACCESS_TOKEN_TYPE)
                .subject("00000000-0000-7000-8000-000000000001")
                .issuedAt(Instant.EPOCH)
                .expiresAt(Instant.EPOCH.plusSeconds(60));
        if (securityVersion != null) {
            builder.claim("security_version", securityVersion);
        }
        return builder.build();
    }

    private static final class StubVerifier implements OwnerVerifier {

        private final AtomicReference<Decision> decision = new AtomicReference<>(Decision.FRESH);
        private final AtomicInteger invocations = new AtomicInteger();

        @Override
        public Decision verify(String accessToken) {
            invocations.incrementAndGet();
            return decision.get();
        }

        void decision(Decision next) {
            decision.set(next);
        }

        int invocations() {
            return invocations.get();
        }
    }
}
