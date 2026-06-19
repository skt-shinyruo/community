package com.nowcoder.community.auth.infrastructure.web;

import com.nowcoder.community.auth.application.TokenFreshnessApplicationService;
import com.nowcoder.community.auth.application.result.TokenFreshnessResult;
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
import static org.mockito.Mockito.when;

class TokenFreshnessFilterTest {

    private final TokenFreshnessApplicationService tokenFreshnessApplicationService = mock(TokenFreshnessApplicationService.class);
    private final TokenFreshnessFilter filter = new TokenFreshnessFilter(tokenFreshnessApplicationService);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldBypassNonHighRiskPath() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users/abc");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(tokenFreshnessApplicationService, never()).verify(null, 0L);
    }

    @Test
    void shouldRejectStaleHighRiskToken() throws Exception {
        UUID userId = uuid(7);
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
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

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }
}
