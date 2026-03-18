package com.nowcoder.community.auth.service;

import com.nowcoder.community.auth.api.AuthErrorCode;
import com.nowcoder.community.contracts.exception.BusinessException;
import com.nowcoder.community.infra.security.jwt.JwtProperties;
import com.nowcoder.community.infra.web.net.ClientIpResolver;
import com.nowcoder.community.user.api.internal.dto.UserInternalSessionProfileResponse;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RefreshTokenServiceTest {

    @Test
    void refreshShouldRejectReplayWhenSameTokenIsPresentedConcurrently() throws Exception {
        CoordinatedRefreshTokenStore store = new CoordinatedRefreshTokenStore("presented-token");
        RefreshTokenService refreshTokenService = new RefreshTokenService(jwtProperties(), store);
        store.store("presented-token", 7, "family-1", Instant.now().plusSeconds(300));

        AuthService authService = authService(refreshTokenService);
        MockHttpServletRequest request = refreshRequest("presented-token");

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<RefreshAttempt> first = executor.submit(() -> attemptRefresh(authService, request));
            Future<RefreshAttempt> second = executor.submit(() -> attemptRefresh(authService, request));

            RefreshAttempt firstAttempt = first.get(5, TimeUnit.SECONDS);
            RefreshAttempt secondAttempt = second.get(5, TimeUnit.SECONDS);

            long successCount = List.of(firstAttempt, secondAttempt).stream().filter(RefreshAttempt::succeeded).count();
            long invalidCount = List.of(firstAttempt, secondAttempt).stream()
                    .map(RefreshAttempt::error)
                    .filter(BusinessException.class::isInstance)
                    .map(BusinessException.class::cast)
                    .filter(e -> e.getErrorCode() == AuthErrorCode.REFRESH_TOKEN_INVALID)
                    .count();

            assertThat(successCount).isEqualTo(1);
            assertThat(invalidCount).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void refreshShouldOnlyRotateOnceForSamePresentedToken() throws Exception {
        CoordinatedRefreshTokenStore store = new CoordinatedRefreshTokenStore("presented-token");
        RefreshTokenService refreshTokenService = new RefreshTokenService(jwtProperties(), store);
        store.store("presented-token", 7, "family-1", Instant.now().plusSeconds(300));

        AuthService authService = authService(refreshTokenService);
        MockHttpServletRequest request = refreshRequest("presented-token");

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<RefreshAttempt> first = executor.submit(() -> attemptRefresh(authService, request));
            Future<RefreshAttempt> second = executor.submit(() -> attemptRefresh(authService, request));

            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        assertThat(store.activeTokensInFamily("family-1"))
                .doesNotContain("presented-token")
                .hasSize(1);
    }

    private static AuthService authService(RefreshTokenService refreshTokenService) {
        UserAuthAccess userAuthAccess = mock(UserAuthAccess.class);
        JwtTokenService jwtTokenService = mock(JwtTokenService.class);
        LoginRateLimitService loginRateLimitService = mock(LoginRateLimitService.class);
        CaptchaService captchaService = mock(CaptchaService.class);
        ClientIpResolver clientIpResolver = mock(ClientIpResolver.class);

        UserInternalSessionProfileResponse profile = new UserInternalSessionProfileResponse();
        profile.setUserId(7);
        profile.setUsername("alice");
        profile.setStatus(1);
        profile.setAuthorities(List.of("user"));

        when(userAuthAccess.sessionProfile(7)).thenReturn(profile);
        when(jwtTokenService.createAccessToken(7, "alice", List.of("user"))).thenReturn("access-token");

        return new AuthService(
                userAuthAccess,
                jwtTokenService,
                refreshTokenService,
                loginRateLimitService,
                captchaService,
                clientIpResolver
        );
    }

    private static JwtProperties jwtProperties() {
        JwtProperties properties = new JwtProperties();
        properties.setHmacSecret("12345678901234567890123456789012");
        properties.setRefreshCookieName("refresh_token");
        properties.setRefreshCookiePath("/api/auth");
        properties.setRefreshTokenTtlSeconds(600);
        return properties;
    }

    private static MockHttpServletRequest refreshRequest(String refreshToken) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("refresh_token", refreshToken));
        return request;
    }

    private static RefreshAttempt attemptRefresh(AuthService authService, MockHttpServletRequest request) {
        try {
            return new RefreshAttempt(authService.refresh(request), null);
        } catch (Throwable error) {
            return new RefreshAttempt(null, error);
        }
    }

    private record RefreshAttempt(AuthService.RefreshResult result, Throwable error) {

        private boolean succeeded() {
            return result != null;
        }
    }

    private static final class CoordinatedRefreshTokenStore implements RefreshTokenStore {

        private final ConcurrentHashMap<String, StoredRefreshToken> tokens = new ConcurrentHashMap<>();
        private final String coordinatedToken;
        private final CyclicBarrier barrier = new CyclicBarrier(2);

        private CoordinatedRefreshTokenStore(String coordinatedToken) {
            this.coordinatedToken = coordinatedToken;
        }

        @Override
        public void store(String refreshToken, int userId, String familyId, Instant expiresAt) {
            tokens.put(refreshToken, new StoredRefreshToken(refreshToken, userId, familyId, expiresAt));
        }

        @Override
        public StoredRefreshToken find(String refreshToken) {
            StoredRefreshToken captured = tokens.get(refreshToken);
            if (!coordinatedToken.equals(refreshToken) || captured == null) {
                return captured;
            }
            awaitBarrier();
            return captured;
        }

        @Override
        public StoredRefreshToken consume(String refreshToken) {
            return tokens.remove(refreshToken);
        }

        @Override
        public void revoke(String refreshToken) {
            tokens.remove(refreshToken);
        }

        @Override
        public void revokeFamily(String familyId) {
            tokens.entrySet().removeIf(entry -> familyId.equals(entry.getValue().familyId()));
        }

        private Set<String> activeTokensInFamily(String familyId) {
            return tokens.values().stream()
                    .filter(token -> familyId.equals(token.familyId()))
                    .map(StoredRefreshToken::refreshToken)
                    .collect(Collectors.toSet());
        }

        private void awaitBarrier() {
            try {
                barrier.await(5, TimeUnit.SECONDS);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}
