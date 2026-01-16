package com.nowcoder.community.auth.api;

import com.nowcoder.community.auth.config.JwtProperties;
import com.nowcoder.community.auth.api.dto.LoginRequest;
import com.nowcoder.community.auth.api.dto.LoginResponse;
import com.nowcoder.community.auth.api.dto.MeResponse;
import com.nowcoder.community.auth.service.AuthService;
import com.nowcoder.community.common.api.Result;
import com.nowcoder.community.common.trace.TraceId;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import java.time.Duration;
import java.util.List;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final JwtProperties jwtProperties;

    public AuthController(AuthService authService, JwtProperties jwtProperties) {
        this.authService = authService;
        this.jwtProperties = jwtProperties;
    }

    @PostMapping("/login")
    public ResponseEntity<Result<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        AuthService.LoginResult result = authService.login(request.getUsername(), request.getPassword());

        ResponseCookie refreshCookie = buildRefreshCookie(result.refreshToken());
        LoginResponse response = new LoginResponse(result.accessToken(), result.expiresInSeconds(), result.userId(), result.roles());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(Result.ok(response));
    }

    @PostMapping("/refresh")
    public ResponseEntity<Result<LoginResponse>> refresh(@CookieValue(name = "refresh_token", required = false) String refreshToken,
                                                        HttpServletRequest request) {
        String origin = request.getHeader("Origin");
        AuthService.LoginResult result = authService.refresh(refreshToken, origin);

        ResponseCookie refreshCookie = buildRefreshCookie(result.refreshToken());
        LoginResponse response = new LoginResponse(result.accessToken(), result.expiresInSeconds(), result.userId(), result.roles());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(Result.ok(response));
    }

    @PostMapping("/logout")
    public ResponseEntity<Result<Void>> logout(@CookieValue(name = "refresh_token", required = false) String refreshToken,
                                               Authentication authentication) {
        Integer userId = null;
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            userId = Integer.parseInt(jwt.getSubject());
        }

        authService.logout(refreshToken, userId);

        ResponseCookie clearCookie = clearRefreshCookie();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, clearCookie.toString())
                .body(Result.ok());
    }

    @GetMapping("/me")
    public Result<MeResponse> me(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            return Result.ok(new MeResponse(0, List.of(), TraceId.currentOrNull()));
        }

        int userId = Integer.parseInt(jwt.getSubject());
        @SuppressWarnings("unchecked")
        List<String> roles = (List<String>) jwt.getClaims().getOrDefault("roles", List.of());

        return Result.ok(new MeResponse(userId, roles, TraceId.currentOrNull()));
    }

    private ResponseCookie buildRefreshCookie(String refreshToken) {
        return ResponseCookie.from(jwtProperties.getRefreshCookieName(), refreshToken)
                .httpOnly(true)
                .secure(jwtProperties.isRefreshCookieSecure())
                .path(jwtProperties.getRefreshCookiePath())
                .sameSite(jwtProperties.getRefreshCookieSameSite())
                .maxAge(Duration.ofSeconds(jwtProperties.getRefreshTokenTtlSeconds()))
                .build();
    }

    private ResponseCookie clearRefreshCookie() {
        return ResponseCookie.from(jwtProperties.getRefreshCookieName(), "")
                .httpOnly(true)
                .secure(jwtProperties.isRefreshCookieSecure())
                .path(jwtProperties.getRefreshCookiePath())
                .sameSite(jwtProperties.getRefreshCookieSameSite())
                .maxAge(Duration.ZERO)
                .build();
    }
}

