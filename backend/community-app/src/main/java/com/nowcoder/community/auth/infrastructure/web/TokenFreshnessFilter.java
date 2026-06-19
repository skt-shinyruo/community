package com.nowcoder.community.auth.infrastructure.web;

import com.nowcoder.community.auth.application.TokenFreshnessApplicationService;
import com.nowcoder.community.auth.application.result.TokenFreshnessResult;
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
import java.util.List;
import java.util.UUID;

@Component
public class TokenFreshnessFilter extends OncePerRequestFilter {

    private static final List<String> HIGH_RISK_PREFIXES = List.of(
            "/api/users/admin/",
            "/api/ops/",
            "/api/admin/market/",
            "/api/wallet/admin/"
    );

    private final TokenFreshnessApplicationService tokenFreshnessApplicationService;

    public TokenFreshnessFilter(TokenFreshnessApplicationService tokenFreshnessApplicationService) {
        this.tokenFreshnessApplicationService = tokenFreshnessApplicationService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!isHighRisk(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        Jwt jwt = currentJwt();
        UUID userId = parseSubject(jwt);
        Number versionClaim = jwt == null ? null : jwt.getClaim("security_version");
        long version = versionClaim == null ? 0L : versionClaim.longValue();
        TokenFreshnessResult result = tokenFreshnessApplicationService.verify(userId, version);
        if (result.status() == TokenFreshnessResult.Status.ACCEPTED) {
            filterChain.doFilter(request, response);
            return;
        }
        response.setStatus(result.status() == TokenFreshnessResult.Status.STALE
                ? HttpServletResponse.SC_UNAUTHORIZED
                : HttpServletResponse.SC_FORBIDDEN);
    }

    private boolean isHighRisk(HttpServletRequest request) {
        String path = request == null ? "" : request.getRequestURI();
        return HIGH_RISK_PREFIXES.stream().anyMatch(path::startsWith);
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
