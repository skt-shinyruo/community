package com.nowcoder.community.auth.infrastructure.web;

import com.nowcoder.community.auth.application.TokenFreshnessApplicationService;
import com.nowcoder.community.auth.application.TokenFreshnessApplicationService.TokenFreshnessResult;
import com.nowcoder.community.common.security.jwt.JwtCodecs;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TokenFreshnessFilterTest {

    private final TokenFreshnessApplicationService tokenFreshnessApplicationService = mock(TokenFreshnessApplicationService.class);
    private final TokenFreshnessFilter filter = new TokenFreshnessFilter(tokenFreshnessApplicationService);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldVerifyEveryAuthenticatedPath() throws Exception {
        UUID userId = uuid(6);
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .header("typ", JwtCodecs.ACCESS_TOKEN_TYPE)
                .subject(userId.toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .claim("security_version", 3L)
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
        when(tokenFreshnessApplicationService.verify(userId, 3L))
                .thenReturn(TokenFreshnessResult.accepted());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users/abc");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(tokenFreshnessApplicationService).verify(userId, 3L);
    }

    @Test
    void shouldRejectStaleHighRiskToken() throws Exception {
        UUID userId = uuid(7);
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .header("typ", JwtCodecs.ACCESS_TOKEN_TYPE)
                .subject(userId.toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .claim("security_version", 9L)
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
        when(tokenFreshnessApplicationService.verify(userId, 9L))
                .thenReturn(TokenFreshnessResult.stale());
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/users/admin/role");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void shouldBypassServiceToken() throws Exception {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .header("typ", JwtCodecs.SERVICE_TOKEN_TYPE)
                .subject(uuid(8).toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .claim("scope", "im.realtime.internal")
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET",
                "/internal/im/realtime/projections/user-policies"
        );
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verifyNoInteractions(tokenFreshnessApplicationService);
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }
}
