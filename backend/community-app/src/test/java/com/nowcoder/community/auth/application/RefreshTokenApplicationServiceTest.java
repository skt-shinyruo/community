package com.nowcoder.community.auth.application;

import com.nowcoder.community.analytics.api.action.AnalyticsIngestActionApi;
import com.nowcoder.community.auth.application.command.RefreshCommand;
import com.nowcoder.community.auth.application.port.AuthTokenPort;
import com.nowcoder.community.auth.application.result.RefreshResult;
import com.nowcoder.community.auth.domain.repository.RefreshTokenRepository;
import com.nowcoder.community.auth.domain.service.AuthDomainService;
import com.nowcoder.community.auth.domain.service.RefreshTokenDomainService;
import com.nowcoder.community.auth.exception.AuthErrorCode;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.security.jwt.JwtProperties;
import com.nowcoder.community.user.api.action.UserRefreshTokenSessionActionApi;
import com.nowcoder.community.user.api.model.UserCredentialView;
import com.nowcoder.community.user.api.query.UserCredentialQueryApi;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RefreshTokenApplicationServiceTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-7000-8000-000000000007");

    @Test
    void authServiceShouldOnlyExposeFocusedServiceConstructor() {
        assertThat(LoginApplicationService.class.getDeclaredConstructors())
                .singleElement()
                .satisfies(constructor -> assertThat(constructor.getParameterTypes()).containsExactly(
                        UserCredentialQueryApi.class,
                        AuthTokenPort.class,
                        RefreshTokenApplicationService.class,
                        LoginRateLimitApplicationService.class,
                        CaptchaApplicationService.class,
                        AuthDomainService.class,
                        AnalyticsIngestActionApi.class
                ));
    }

    @Test
    void refreshShouldRejectReplayWhenSameTokenIsPresentedConcurrently() throws Exception {
        CoordinatedRefreshTokenStore store = new CoordinatedRefreshTokenStore("presented-token");
        RefreshTokenApplicationService refreshTokenService = refreshTokenService(store);
        store.store("presented-token", USER_ID, "family-1", Instant.now().plusSeconds(300));

        LoginApplicationService authService = authService(refreshTokenService);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<RefreshAttempt> first = executor.submit(() -> attemptRefresh(authService, "presented-token"));
            Future<RefreshAttempt> second = executor.submit(() -> attemptRefresh(authService, "presented-token"));

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
        RefreshTokenApplicationService refreshTokenService = refreshTokenService(store);
        store.store("presented-token", USER_ID, "family-1", Instant.now().plusSeconds(300));

        LoginApplicationService authService = authService(refreshTokenService);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<RefreshAttempt> first = executor.submit(() -> attemptRefresh(authService, "presented-token"));
            Future<RefreshAttempt> second = executor.submit(() -> attemptRefresh(authService, "presented-token"));

            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        assertThat(store.activeTokensInFamily("family-1"))
                .doesNotContain("presented-token")
                .hasSize(1);
    }

    @Test
    void refreshShouldBuildAccessTokenFromCredentialLookupOnly() {
        RefreshTokenApplicationService refreshTokenService = refreshTokenService(new InMemoryRefreshTokenRepository());
        RefreshTokenApplicationService.IssuedRefreshToken issued = refreshTokenService.issue(USER_ID);
        LoginApplicationService authService = authService(refreshTokenService, new UserCredentialView(USER_ID, "alice", 1, 2, "h1"));

        RefreshResult result = authService.refresh(new RefreshCommand(issued.refreshToken()));

        assertThat(result.accessToken()).isEqualTo("access-token");
    }

    @Test
    void rotateShouldReturnNullWhenStoreThrows() {
        RefreshTokenApplicationService refreshTokenService = refreshTokenService(new RefreshTokenRepository() {
            @Override
            public void store(String refreshToken, UUID userId, String familyId, Instant expiresAt) {
                throw new IllegalStateException("store rejected");
            }

            @Override
            public StoredRefreshToken find(String refreshToken) {
                return null;
            }

            @Override
            public StoredRefreshToken consume(String refreshToken) {
                return new StoredRefreshToken(refreshToken, USER_ID, "family-1", Instant.now().plusSeconds(300));
            }

            @Override
            public void revoke(String refreshToken) {
            }

            @Override
            public void revokeFamily(String familyId) {
            }
        });

        assertThatCode(() -> assertThat(refreshTokenService.rotate("presented-token")).isNull())
                .doesNotThrowAnyException();
    }

    private static LoginApplicationService authService(RefreshTokenApplicationService refreshTokenService) {
        return authService(refreshTokenService, new UserCredentialView(USER_ID, "alice", 1, 0, "h1"));
    }

    private static LoginApplicationService authService(RefreshTokenApplicationService refreshTokenService, UserCredentialView credentialView) {
        UserCredentialQueryApi userCredentialQueryApi = mock(UserCredentialQueryApi.class);
        AuthTokenPort authTokenPort = mock(AuthTokenPort.class);
        LoginRateLimitApplicationService loginRateLimitService = mock(LoginRateLimitApplicationService.class);
        CaptchaApplicationService captchaService = mock(CaptchaApplicationService.class);

        when(userCredentialQueryApi.getByUserId(USER_ID)).thenReturn(credentialView);
        when(userCredentialQueryApi.authoritiesOf(argThat(user ->
                user != null
                        && user.userId().equals(USER_ID)
                        && user.username().equals("alice")
                        && user.status() == 1
                        && user.type() == credentialView.type()
                        && user.headerUrl().equals("h1")
        ))).thenReturn(List.of("user"));
        when(authTokenPort.createAccessToken(USER_ID, "alice", List.of("user"))).thenReturn("access-token");

        return new LoginApplicationService(
                userCredentialQueryApi,
                authTokenPort,
                refreshTokenService,
                loginRateLimitService,
                captchaService,
                new AuthDomainService(),
                mock(AnalyticsIngestActionApi.class)
        );
    }

    private static RefreshTokenApplicationService refreshTokenService(RefreshTokenRepository repository) {
        return new RefreshTokenApplicationService(
                jwtProperties(),
                repository,
                new RefreshTokenDomainService(),
                mock(UserRefreshTokenSessionActionApi.class)
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

    private static RefreshAttempt attemptRefresh(LoginApplicationService authService, String refreshToken) {
        try {
            return new RefreshAttempt(authService.refresh(new RefreshCommand(refreshToken)), null);
        } catch (Throwable error) {
            return new RefreshAttempt(null, error);
        }
    }

    private record RefreshAttempt(RefreshResult result, Throwable error) {

        private boolean succeeded() {
            return result != null;
        }
    }

    private static final class CoordinatedRefreshTokenStore implements RefreshTokenRepository {

        private final ConcurrentHashMap<String, StoredRefreshToken> tokens = new ConcurrentHashMap<>();
        private final String coordinatedToken;
        private final CyclicBarrier barrier = new CyclicBarrier(2);

        private CoordinatedRefreshTokenStore(String coordinatedToken) {
            this.coordinatedToken = coordinatedToken;
        }

        @Override
        public void store(String refreshToken, UUID userId, String familyId, Instant expiresAt) {
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

    private static final class InMemoryRefreshTokenRepository implements RefreshTokenRepository {

        private final ConcurrentHashMap<String, StoredRefreshToken> tokens = new ConcurrentHashMap<>();

        @Override
        public void store(String refreshToken, UUID userId, String familyId, Instant expiresAt) {
            tokens.put(refreshToken, new StoredRefreshToken(refreshToken, userId, familyId, expiresAt));
        }

        @Override
        public StoredRefreshToken find(String refreshToken) {
            return tokens.get(refreshToken);
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
    }
}
