package com.nowcoder.community.auth.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.analytics.ingest.AnalyticsIngestService;
import com.nowcoder.community.auth.exception.AuthErrorCode;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.web.net.ClientIpResolver;
import com.nowcoder.community.user.api.model.UserAuthenticationResultView;
import com.nowcoder.community.user.api.model.UserCredentialView;
import com.nowcoder.community.user.api.query.UserCredentialQueryApi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.logging.LoggingInitializationContext;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.ResponseCookie;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class AuthServiceLoginTest {

    private static final String SERVICE_VERSION = "test-service-version";

    private final UserCredentialQueryApi userCredentialQueryApi = mock(UserCredentialQueryApi.class);
    private final JwtTokenService jwtTokenService = mock(JwtTokenService.class);
    private final RefreshTokenService refreshTokenService = mock(RefreshTokenService.class);
    private final LoginRateLimitService loginRateLimitService = mock(LoginRateLimitService.class);
    private final CaptchaService captchaService = mock(CaptchaService.class);
    private final ClientIpResolver clientIpResolver = mock(ClientIpResolver.class);
    private final AnalyticsIngestService analyticsIngestService = mock(AnalyticsIngestService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final LoggingSystem loggingSystem = LoggingSystem.get(getClass().getClassLoader());

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                userCredentialQueryApi,
                jwtTokenService,
                refreshTokenService,
                loginRateLimitService,
                captchaService,
                clientIpResolver,
                analyticsIngestService
        );
        when(clientIpResolver.resolve(any())).thenReturn(new ClientIpResolver.ResolvedClientIp("127.0.0.1", ClientIpResolver.SOURCE_REMOTE));
    }

    @AfterEach
    void tearDown() {
        loggingSystem.cleanUp();
    }

    @Test
    void authServiceShouldOnlyExposeUserApiConstructor() {
        assertThat(AuthService.class.getDeclaredConstructors())
                .singleElement()
                .satisfies(constructor -> assertThat(constructor.getParameterTypes()).containsExactly(
                        UserCredentialQueryApi.class,
                        JwtTokenService.class,
                        RefreshTokenService.class,
                        LoginRateLimitService.class,
                        CaptchaService.class,
                        ClientIpResolver.class,
                        AnalyticsIngestService.class
                ));
    }

    @Test
    void authServiceShouldNotExposeGenericIssueLoginResultBridge() {
        assertThatThrownBy(() -> AuthService.class.getDeclaredMethod("issueLoginResult", Object.class))
                .isInstanceOf(NoSuchMethodException.class);

        assertThat(Arrays.stream(AuthService.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("issueLoginResult"))
                .map(Method::getParameterTypes)
                .toList())
                .containsExactly(new Class<?>[]{UserCredentialView.class});
    }

    @Test
    void loginShouldRecordFailureWhenCredentialsAreInvalid(CapturedOutput output) {
        when(userCredentialQueryApi.authenticate("alice", "wrong-password"))
                .thenReturn(UserAuthenticationResultView.invalidCredentials());

        Throwable thrown = catchThrowable(() -> authService.login("alice", "wrong-password", null, null, new MockHttpServletRequest()));

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

        Throwable thrown = catchThrowable(() -> authService.login("alice", "secret", null, null, new MockHttpServletRequest()));

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

        ResponseCookie cookie = ResponseCookie.from("refresh_token", "rt").path("/api/auth").httpOnly(true).build();
        when(userCredentialQueryApi.authoritiesOf(user)).thenReturn(List.of("ROLE_USER"));
        when(jwtTokenService.createAccessToken(eq(userId), eq("alice"), eq(List.of("ROLE_USER")))).thenReturn("access-token");
        when(refreshTokenService.issue(userId)).thenReturn(new RefreshTokenService.IssuedRefreshToken("rt", cookie));

        AuthService.LoginResult result = authService.login("alice", "secret", null, null, new MockHttpServletRequest());

        assertThat(result.accessToken()).isEqualTo("access-token");
        assertThat(result.refreshCookie()).isEqualTo(cookie);
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
        when(jwtTokenService.createAccessToken(eq(userId), eq("alice"), anyList())).thenReturn("access-token");
        when(refreshTokenService.issue(userId)).thenReturn(new RefreshTokenService.IssuedRefreshToken("refresh-token", ResponseCookie.from("refresh_token", "refresh-token").build()));
        when(clientIpResolver.resolve(any())).thenReturn(new ClientIpResolver.ResolvedClientIp("1.1.1.1", ClientIpResolver.SOURCE_REMOTE));

        authService.login("alice", "pw", null, null, new MockHttpServletRequest());

        verify(analyticsIngestService).recordLoginSuccess(userId);
    }

    @Test
    void loginShouldLogDeniedWhenCaptchaIsRequiredButMissing(CapturedOutput output) {
        when(loginRateLimitService.isCaptchaRequired("alice", "127.0.0.1")).thenReturn(true);

        Throwable thrown = catchThrowable(() -> authService.login("alice", "secret", "cid", "", new MockHttpServletRequest()));

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

        Throwable thrown = catchThrowable(() -> authService.login("alice", "secret", "cid", "bad-code", new MockHttpServletRequest()));

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
        when(jwtTokenService.createAccessToken(eq(userId), eq("alice"), eq(List.of("ROLE_USER")))).thenReturn("access-token");
        when(refreshTokenService.issue(userId)).thenThrow(new RuntimeException("issue failed"));

        Throwable thrown = catchThrowable(() -> authService.login("alice", "secret", null, null, new MockHttpServletRequest()));

        assertThat(thrown).isInstanceOf(RuntimeException.class).hasMessage("issue failed");
        assertThat(output.getAll()).doesNotContain("community.category=security community.action=login community.outcome=success");
    }

    @Test
    void loginShouldEncodeUnsafeCharactersInSecurityEventTokens(CapturedOutput output) {
        String spoofedUsername = "alice bob=\nroot";
        when(userCredentialQueryApi.authenticate(spoofedUsername, "secret"))
                .thenReturn(UserAuthenticationResultView.invalidCredentials());

        Throwable thrown = catchThrowable(() -> authService.login(spoofedUsername, "secret", null, null, new MockHttpServletRequest()));

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

        Throwable thrown = catchThrowable(() -> authService.login("alice", "wrong-password", null, null, new MockHttpServletRequest()));

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
                .filter(event -> event != null && AuthService.class.getName().equals(event.path("logger").asText()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No structured log event found for " + AuthService.class.getName() + " in output: " + output.getAll()));
    }

    private JsonNode readJson(String line) {
        try {
            return objectMapper.readTree(line);
        } catch (IOException ex) {
            return null;
        }
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }
}
