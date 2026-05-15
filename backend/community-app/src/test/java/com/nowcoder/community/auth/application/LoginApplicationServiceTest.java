package com.nowcoder.community.auth.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.analytics.api.action.AnalyticsIngestActionApi;
import com.nowcoder.community.auth.application.command.LoginCommand;
import com.nowcoder.community.auth.application.command.RefreshCommand;
import com.nowcoder.community.auth.application.port.AuthTokenPort;
import com.nowcoder.community.auth.application.result.LoginResult;
import com.nowcoder.community.auth.application.result.RefreshCookieSpec;
import com.nowcoder.community.auth.application.result.RefreshResult;
import com.nowcoder.community.auth.domain.repository.RefreshTokenRepository;
import com.nowcoder.community.auth.domain.service.AuthDomainService;
import com.nowcoder.community.auth.exception.AuthErrorCode;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.web.net.ClientIpResolver;
import com.nowcoder.community.user.api.model.UserAuthenticationResultView;
import com.nowcoder.community.user.api.model.UserCredentialView;
import com.nowcoder.community.user.api.query.UserCredentialQueryApi;
import com.nowcoder.community.user.exception.UserErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.logging.LoggingInitializationContext;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.env.MockEnvironment;

import java.io.IOException;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class LoginApplicationServiceTest {

    private static final String SERVICE_VERSION = "test-service-version";

    private final UserCredentialQueryApi userCredentialQueryApi = mock(UserCredentialQueryApi.class);
    private final AuthTokenPort authTokenPort = mock(AuthTokenPort.class);
    private final RefreshTokenApplicationService refreshTokenService = mock(RefreshTokenApplicationService.class);
    private final LoginRateLimitApplicationService loginRateLimitService = mock(LoginRateLimitApplicationService.class);
    private final CaptchaApplicationService captchaService = mock(CaptchaApplicationService.class);
    private final AnalyticsIngestActionApi analyticsIngestService = mock(AnalyticsIngestActionApi.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final LoggingSystem loggingSystem = LoggingSystem.get(getClass().getClassLoader());

    private LoginApplicationService authService;

    @BeforeEach
    void setUp() {
        authService = new LoginApplicationService(
                userCredentialQueryApi,
                authTokenPort,
                refreshTokenService,
                loginRateLimitService,
                captchaService,
                new AuthDomainService(),
                analyticsIngestService
        );
    }

    @AfterEach
    void tearDown() {
        loggingSystem.cleanUp();
    }

    @Test
    void authServiceShouldOnlyExposeUserApiConstructor() {
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
    void authServiceShouldNotExposeGenericIssueLoginResultBridge() {
        assertThatThrownBy(() -> LoginApplicationService.class.getDeclaredMethod("issueLoginResult", Object.class))
                .isInstanceOf(NoSuchMethodException.class);

        assertThat(Arrays.stream(LoginApplicationService.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("issueLoginResult"))
                .map(Method::getParameterTypes)
                .toList())
                .containsExactly(new Class<?>[]{UserCredentialView.class});
    }

    @Test
    void loginShouldRecordFailureWhenCredentialsAreInvalid(CapturedOutput output) {
        when(userCredentialQueryApi.authenticate("alice", "wrong-password"))
                .thenReturn(UserAuthenticationResultView.invalidCredentials());

        Throwable thrown = catchThrowable(() -> authService.login(loginCommand("alice", "wrong-password", null, null)));

        assertThat(thrown).isInstanceOf(BusinessException.class);
        BusinessException error = (BusinessException) thrown;
        assertThat(error.getErrorCode()).isEqualTo(AuthErrorCode.INVALID_CREDENTIALS);
        verify(loginRateLimitService).recordFailure("alice", "127.0.0.1", ClientIpResolver.SOURCE_REMOTE);
        verify(loginRateLimitService, never()).reset(any(), any());
        assertThat(output.getAll())
                .contains("community.category=security")
                .contains("community.action=login")
                .contains("community.outcome=denied")
                .contains("community.reason_code=invalid_credentials")
                .contains("username=alice")
                .contains("source.ip=127.0.0.1")
                .doesNotContain("wrong-password");
    }

    @Test
    void loginShouldRecordFailureWhenUserIsDisabled(CapturedOutput output) {
        UserCredentialView disabledUser = new UserCredentialView(uuid(7), "alice", 0, 0, "h1");
        when(userCredentialQueryApi.authenticate("alice", "secret"))
                .thenReturn(UserAuthenticationResultView.userDisabled(disabledUser));

        Throwable thrown = catchThrowable(() -> authService.login(loginCommand("alice", "secret", null, null)));

        assertThat(thrown).isInstanceOf(BusinessException.class);
        BusinessException error = (BusinessException) thrown;
        assertThat(error.getErrorCode()).isEqualTo(AuthErrorCode.USER_DISABLED);
        verify(loginRateLimitService).recordFailure("alice", "127.0.0.1", ClientIpResolver.SOURCE_REMOTE);
        verify(loginRateLimitService, never()).reset(any(), any());
        assertThat(output.getAll())
                .contains("community.category=security")
                .contains("community.action=login")
                .contains("community.outcome=denied")
                .contains("community.reason_code=user_disabled")
                .contains("username=alice")
                .contains("source.ip=127.0.0.1")
                .doesNotContain("secret");
    }

    @Test
    void loginShouldResetRateLimitAfterSuccessfulAuthentication(CapturedOutput output) {
        UUID userId = uuid(7);
        UserCredentialView user = new UserCredentialView(userId, "alice", 1, 0, "h1");
        when(userCredentialQueryApi.authenticate("alice", "secret")).thenReturn(UserAuthenticationResultView.authenticated(user));

        RefreshCookieSpec cookie = issuedCookie("rt");
        when(userCredentialQueryApi.authoritiesOf(user)).thenReturn(List.of("ROLE_USER"));
        when(authTokenPort.createAccessToken(eq(userId), eq("alice"), eq(List.of("ROLE_USER")))).thenReturn("access-token");
        when(refreshTokenService.issue(userId)).thenReturn(new RefreshTokenApplicationService.IssuedRefreshToken("rt", cookie));

        LoginResult result = authService.login(loginCommand("alice", "secret", null, null));

        assertThat(result.accessToken()).isEqualTo("access-token");
        assertThat(result.refreshCookie()).isEqualTo(cookie);
        assertThat(result.refreshCookie().value()).isEqualTo("rt");
        verify(loginRateLimitService).reset("alice", "127.0.0.1");
        verify(loginRateLimitService, never()).recordFailure(any(), any(), any());
        assertThat(output.getAll())
                .contains("community.category=security")
                .contains("community.action=login")
                .contains("community.outcome=success")
                .contains("user.id=" + userId)
                .contains("username=alice")
                .contains("source.ip=127.0.0.1")
                .doesNotContain("secret")
                .doesNotContain("access-token");
    }

    @Test
    void loginShouldRecordDauSupplementAfterSuccessfulAuthentication() {
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UserCredentialView user = new UserCredentialView(userId, "alice", 1, 0, null);
        when(userCredentialQueryApi.authenticate("alice", "pw"))
                .thenReturn(UserAuthenticationResultView.authenticated(user));
        when(userCredentialQueryApi.authoritiesOf(user)).thenReturn(List.of("ROLE_USER"));
        when(authTokenPort.createAccessToken(eq(userId), eq("alice"), anyList())).thenReturn("access-token");
        when(refreshTokenService.issue(userId)).thenReturn(new RefreshTokenApplicationService.IssuedRefreshToken("refresh-token", issuedCookie("refresh-token")));

        authService.login(new LoginCommand("alice", "pw", null, null, "1.1.1.1", ClientIpResolver.SOURCE_REMOTE));

        verify(analyticsIngestService).recordLoginSuccess(userId);
    }

    @Test
    void loginShouldLogDeniedWhenCaptchaIsRequiredButMissing(CapturedOutput output) {
        when(loginRateLimitService.isCaptchaRequired("alice", "127.0.0.1")).thenReturn(true);

        Throwable thrown = catchThrowable(() -> authService.login(loginCommand("alice", "secret", "cid", "")));

        assertThat(thrown).isInstanceOf(BusinessException.class);
        BusinessException error = (BusinessException) thrown;
        assertThat(error.getErrorCode()).isEqualTo(AuthErrorCode.CAPTCHA_REQUIRED);
        verify(loginRateLimitService).recordFailure("alice", "127.0.0.1", ClientIpResolver.SOURCE_REMOTE);
        assertThat(output.getAll())
                .contains("community.category=security")
                .contains("community.action=login")
                .contains("community.outcome=denied")
                .contains("community.reason_code=captcha_required")
                .contains("username=alice")
                .contains("source.ip=127.0.0.1")
                .doesNotContain("secret")
                .doesNotContain("cid");
    }

    @Test
    void loginShouldLogDeniedWhenCaptchaIsInvalid(CapturedOutput output) {
        when(loginRateLimitService.isCaptchaRequired("alice", "127.0.0.1")).thenReturn(true);
        when(captchaService.verify("cid", "bad-code")).thenReturn(false);

        Throwable thrown = catchThrowable(() -> authService.login(loginCommand("alice", "secret", "cid", "bad-code")));

        assertThat(thrown).isInstanceOf(BusinessException.class);
        BusinessException error = (BusinessException) thrown;
        assertThat(error.getErrorCode()).isEqualTo(AuthErrorCode.CAPTCHA_INVALID);
        verify(loginRateLimitService).recordFailure("alice", "127.0.0.1", ClientIpResolver.SOURCE_REMOTE);
        assertThat(output.getAll())
                .contains("community.category=security")
                .contains("community.action=login")
                .contains("community.outcome=denied")
                .contains("community.reason_code=captcha_invalid")
                .contains("username=alice")
                .contains("source.ip=127.0.0.1")
                .doesNotContain("secret")
                .doesNotContain("bad-code")
                .doesNotContain("cid");
    }

    @Test
    void loginShouldNotLogSuccessWhenTokenIssuanceFails(CapturedOutput output) {
        UUID userId = uuid(7);
        UserCredentialView user = new UserCredentialView(userId, "alice", 1, 0, "h1");
        when(userCredentialQueryApi.authenticate("alice", "secret")).thenReturn(UserAuthenticationResultView.authenticated(user));
        when(userCredentialQueryApi.authoritiesOf(user)).thenReturn(List.of("ROLE_USER"));
        when(authTokenPort.createAccessToken(eq(userId), eq("alice"), eq(List.of("ROLE_USER")))).thenReturn("access-token");
        when(refreshTokenService.issue(userId)).thenThrow(new RuntimeException("issue failed"));

        Throwable thrown = catchThrowable(() -> authService.login(loginCommand("alice", "secret", null, null)));

        assertThat(thrown).isInstanceOf(RuntimeException.class).hasMessage("issue failed");
        assertThat(output.getAll()).doesNotContain("community.category=security community.action=login community.outcome=success");
    }

    @Test
    void refreshShouldLetRefreshTokenServiceDecideInvalidTokenSoReplayDetectionCanRun() {
        when(refreshTokenService.consume("replayed-token")).thenReturn(null);

        Throwable thrown = catchThrowable(() -> authService.refresh(new RefreshCommand("replayed-token")));

        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(AuthErrorCode.REFRESH_TOKEN_INVALID);
        verify(refreshTokenService).consume("replayed-token");
        verify(refreshTokenService, never()).find("replayed-token");
        verify(userCredentialQueryApi, never()).getByUserId(any());
    }

    @Test
    void refreshShouldValidateUserBeforeIssuingReplacementRefreshToken() {
        UUID userId = uuid(9);
        RefreshTokenRepository.StoredRefreshToken consumed =
                new RefreshTokenRepository.StoredRefreshToken("old-refresh", userId, "family-1", Instant.now().plusSeconds(600));
        UserCredentialView disabled = new UserCredentialView(userId, "alice", 0, 0, "h1");
        when(refreshTokenService.consume("old-refresh")).thenReturn(consumed);
        when(userCredentialQueryApi.getByUserId(userId)).thenReturn(disabled);

        Throwable thrown = catchThrowable(() -> authService.refresh(new RefreshCommand("old-refresh")));

        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(AuthErrorCode.USER_DISABLED);
        verify(refreshTokenService, never()).issueInFamily(any(UUID.class), anyString());
        verify(refreshTokenService).revokeFamily("family-1");
    }

    @Test
    void refreshShouldMapMissingUserToUserDisabledAndRevokeRefreshFamily() {
        UUID userId = uuid(10);
        RefreshTokenRepository.StoredRefreshToken consumed =
                new RefreshTokenRepository.StoredRefreshToken("old-refresh", userId, "family-2", Instant.now().plusSeconds(600));
        when(refreshTokenService.consume("old-refresh")).thenReturn(consumed);
        when(userCredentialQueryApi.getByUserId(userId)).thenThrow(new BusinessException(UserErrorCode.USER_NOT_FOUND));

        Throwable thrown = catchThrowable(() -> authService.refresh(new RefreshCommand("old-refresh")));

        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(AuthErrorCode.USER_DISABLED);
        verify(refreshTokenService, never()).issueInFamily(any(UUID.class), anyString());
        verify(refreshTokenService).revokeFamily("family-2");
    }

    @Test
    void refreshShouldIssueReplacementOnlyAfterUserIsActive() {
        UUID userId = uuid(11);
        UserCredentialView user = new UserCredentialView(userId, "alice", 1, 0, "h1");
        RefreshCookieSpec cookie = issuedCookie("new-refresh");
        RefreshTokenRepository.StoredRefreshToken consumed =
                new RefreshTokenRepository.StoredRefreshToken("old-refresh", userId, "family-3", Instant.now().plusSeconds(600));
        when(refreshTokenService.consume("old-refresh")).thenReturn(consumed);
        when(userCredentialQueryApi.getByUserId(userId)).thenReturn(user);
        when(userCredentialQueryApi.authoritiesOf(user)).thenReturn(List.of("ROLE_USER"));
        when(authTokenPort.createAccessToken(userId, "alice", List.of("ROLE_USER"))).thenReturn("access-token");
        when(refreshTokenService.issueInFamily(userId, "family-3"))
                .thenReturn(new RefreshTokenApplicationService.IssuedRefreshToken("new-refresh", cookie));

        RefreshResult result = authService.refresh(new RefreshCommand("old-refresh"));

        assertThat(result.accessToken()).isEqualTo("access-token");
        assertThat(result.refreshCookie()).isEqualTo(cookie);
        verify(refreshTokenService).issueInFamily(userId, "family-3");
        verify(refreshTokenService, never()).find("new-refresh");
    }

    @Test
    void loginShouldEncodeUnsafeCharactersInSecurityEventTokens(CapturedOutput output) {
        String spoofedUsername = "alice bob=\nroot";
        when(userCredentialQueryApi.authenticate(spoofedUsername, "secret"))
                .thenReturn(UserAuthenticationResultView.invalidCredentials());

        Throwable thrown = catchThrowable(() -> authService.login(loginCommand(spoofedUsername, "secret", null, null)));

        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(output.getAll())
                .contains("username=alice%20bob%3D%0Aroot")
                .doesNotContain("username=alice bob=")
                .doesNotContain("community.reason_code=invalid_credentials username=alice bob=\nroot");
    }

    @Test
    void loginDeniedShouldExposeCommunityFieldsAsTopLevelJsonInProductionLogging(CapturedOutput output) {
        initializeProductionLogging("community-app");
        when(userCredentialQueryApi.authenticate("alice", "wrong-password"))
                .thenReturn(UserAuthenticationResultView.invalidCredentials());

        Throwable thrown = catchThrowable(() -> authService.login(loginCommand("alice", "wrong-password", null, null)));

        assertThat(thrown).isInstanceOf(BusinessException.class);

        JsonNode event = findJsonEvent(output);
        assertThat(event.path("service.name").asText()).isEqualTo("community-app");
        assertThat(event.path("service.version").asText()).isEqualTo(SERVICE_VERSION);
        assertThat(event.path("community.category").asText()).isEqualTo("security");
        assertThat(event.path("community.action").asText()).isEqualTo("login");
        assertThat(event.path("community.outcome").asText()).isEqualTo("denied");
        assertThat(event.path("message").asText())
                .contains("community.reason_code=invalid_credentials")
                .contains("source.ip=127.0.0.1");
    }

    private void initializeProductionLogging(String serviceName) {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("spring.application.name", serviceName);
        environment.setProperty("community.logging.service-version", SERVICE_VERSION);
        environment.setProperty("spring.profiles.active", "prod");

        loggingSystem.cleanUp();
        loggingSystem.beforeInitialize();
        loggingSystem.initialize(new LoggingInitializationContext(environment), "classpath:logback-spring.xml", null);
    }

    private JsonNode findJsonEvent(CapturedOutput output) {
        return Arrays.stream(output.getAll().split("\\R"))
                .map(String::trim)
                .filter(line -> !line.isEmpty() && line.startsWith("{"))
                .map(this::readJson)
                .filter(event -> event != null && LoginApplicationService.class.getName().equals(event.path("logger").asText()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No structured log event found for " + LoginApplicationService.class.getName() + " in output: " + output.getAll()));
    }

    private JsonNode readJson(String line) {
        try {
            return objectMapper.readTree(line);
        } catch (IOException ex) {
            return null;
        }
    }

    private static LoginCommand loginCommand(String username, String password, String captchaId, String captchaCode) {
        return new LoginCommand(username, password, captchaId, captchaCode, "127.0.0.1", ClientIpResolver.SOURCE_REMOTE);
    }

    private static RefreshCookieSpec issuedCookie(String value) {
        return new RefreshCookieSpec(
                "refresh_token",
                value,
                true,
                false,
                "/api/auth",
                "Lax",
                600
        );
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }
}
