package com.nowcoder.community.auth.infrastructure.web;

import com.nowcoder.community.auth.application.TokenFreshnessApplicationService;
import com.nowcoder.community.auth.application.TokenFreshnessApplicationService.TokenFreshnessResult;
import com.nowcoder.community.common.security.jwt.AccessTokenClaims;
import com.nowcoder.community.common.security.jwt.JwtCodecs;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
public class TokenFreshnessFilter extends OncePerRequestFilter {

    private final TokenFreshnessApplicationService tokenFreshnessApplicationService;

    public TokenFreshnessFilter(TokenFreshnessApplicationService tokenFreshnessApplicationService) {
        this.tokenFreshnessApplicationService = tokenFreshnessApplicationService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Jwt jwt = currentJwt();
        if (jwt == null || !JwtCodecs.ACCESS_TOKEN_TYPE.equals(jwt.getHeaders().get("typ"))) {
            filterChain.doFilter(request, response);
            return;
        }

        UUID userId = parseSubject(jwt);
        long version = AccessTokenClaims.securityVersion(jwt).orElse(0L);
        TokenFreshnessResult result = tokenFreshnessApplicationService.verify(userId, version);
        if (result.status() == TokenFreshnessResult.Status.ACCEPTED) {
            filterChain.doFilter(request, response);
            return;
        }
        response.setStatus(result.status() == TokenFreshnessResult.Status.STALE
                ? HttpServletResponse.SC_UNAUTHORIZED
                : HttpServletResponse.SC_FORBIDDEN);
    }

    private Jwt currentJwt() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Object principal = authentication == null ? null : authentication.getPrincipal();
        return principal instanceof Jwt jwt ? jwt : null;
    }

    private UUID parseSubject(Jwt jwt) {
        if (jwt == null) {
            return null;
        }
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
