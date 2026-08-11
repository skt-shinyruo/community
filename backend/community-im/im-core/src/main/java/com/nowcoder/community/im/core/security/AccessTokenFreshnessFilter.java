package com.nowcoder.community.im.core.security;

import com.nowcoder.community.common.security.jwt.AccessTokenClaims;
import com.nowcoder.community.common.security.jwt.JwtCodecs;
import com.nowcoder.community.im.core.application.AccessTokenFreshnessApplicationService;
import com.nowcoder.community.im.core.application.AccessTokenFreshnessApplicationService.Decision;
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

@Component
public class AccessTokenFreshnessFilter extends OncePerRequestFilter {

    private final AccessTokenFreshnessApplicationService applicationService;

    public AccessTokenFreshnessFilter(AccessTokenFreshnessApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        Jwt jwt = currentJwt();
        if (jwt == null || !JwtCodecs.ACCESS_TOKEN_TYPE.equals(jwt.getHeaders().get("typ"))) {
            filterChain.doFilter(request, response);
            return;
        }
        if (AccessTokenClaims.securityVersion(jwt).isEmpty()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        Decision decision = applicationService.verify(jwt.getTokenValue());
        switch (decision) {
            case FRESH -> filterChain.doFilter(request, response);
            case STALE -> response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            case DENIED -> response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            case UNAVAILABLE -> response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        }
    }

    private Jwt currentJwt() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Object principal = authentication == null ? null : authentication.getPrincipal();
        return principal instanceof Jwt jwt ? jwt : null;
    }
}
