package com.nowcoder.community.auth.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.analytics.api.action.AnalyticsIngestActionApi;
import com.nowcoder.community.auth.application.command.LoginCommand;
import com.nowcoder.community.auth.application.command.LogoutCommand;
import com.nowcoder.community.auth.application.command.RefreshCommand;
import com.nowcoder.community.auth.application.port.AuthTokenPort;
import com.nowcoder.community.auth.application.result.LoginResult;
import com.nowcoder.community.auth.application.result.RefreshFailure;
import com.nowcoder.community.auth.application.result.RefreshCookieSpec;
import com.nowcoder.community.auth.application.result.RefreshResult;
import com.nowcoder.community.auth.domain.repository.RefreshTokenRepository;
import com.nowcoder.community.auth.domain.service.AuthDomainService;
import com.nowcoder.community.auth.exception.AuthErrorCode;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.exception.CommonErrorCode;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class LoginApplicationServiceTest {

    private static final String SERVICE_VERSION = "test-service-version";
    private static final String CAPTCHA_ID = "0123456789abcdef0123456789abcdef";
    private static final UUID ROTATION_LEASE_ID = UUID.fromString("00000000-0000-7000-8000-000000000099");

    private final UserCredentialQueryApi userCredentialQueryApi = mock(UserCredentialQueryApi.class);
    private final AuthTokenPort authTokenPort = mock(AuthTokenPort.class);
    private final RefreshTokenApplicationService refreshTokenService = mock(RefreshTokenApplicationService.class);
    private final LoginRateLimitApplicationService loginRateLimitService = mock(LoginRateLimitApplicationService.class);
    private final CaptchaApplicationService captchaService = mock(CaptchaApplicationService.class);
    private final CaptchaChallengeComponent captchaChallenge = new CaptchaChallengeComponent(captchaService);
    private final AnalyticsIngestActionApi analyticsIngestService = mock(AnalyticsIngestActionApi.class);
    private final LoginTokenIssuer loginTokenIssuer = new LoginTokenIssuer(userCredentialQueryApi, authTokenPort, refreshTokenService);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final LoggingSystem loggingSystem = LoggingSystem.get(getClass().getClassLoader());

    private LoginApplicationService authService;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient().when(userCredentialQueryApi.authenticationSubject(anyString()))
                .thenReturn(new UserCredentialQueryApi.AuthenticationSubject("alice"));
        org.mockito.Mockito.lenient().when(userCredentialQueryApi.prepareAuthentication(anyString()))
                .thenReturn(challenge(null, UserAuthenticationResultView.invalidCredentials()));
        authService = new LoginApplicationService(
                userCredentialQueryApi,
                loginTokenIssuer,
                refreshTokenService,
                loginRateLimitService,
                captchaChallenge,
                new AuthDomainService(),
                analyticsIngestService
        );
    }

    @Test
    void loginShouldAttachAuthoritativeSubjectBeforeLookingUpTheAccount() {
        LoginRateLimitApplicationService.PasswordCheckPermit permit =
                new LoginRateLimitApplicationService.PasswordCheckPermit(uuid(87), List.of("permit"));
        UserCredentialQueryApi.AuthenticationSubject subject =
                new UserCredentialQueryApi.AuthenticationSubject("utf8mb4_unicode_ci:v1:subject-87");
        when(loginRateLimitService.acquirePasswordCheck(
                "alice", "127.0.0.1", ClientIpResolver.SOURCE_REMOTE)).thenReturn(permit);
        when(userCredentialQueryApi.authenticationSubject("alice")).thenReturn(subject);
        when(userCredentialQueryApi.prepareAuthentication("alice"))
                .thenReturn(challenge(null, UserAuthenticationResultView.invalidCredentials()));

        assertThatThrownBy(() -> authService.login(loginCommand("alice", "secret", null, null)))
                .isInstanceOf(BusinessException.class);

        var order = org.mockito.Mockito.inOrder(loginRateLimitService, userCredentialQueryApi);
        order.verify(loginRateLimitService).acquirePasswordCheck(
                "alice", "127.0.0.1", ClientIpResolver.SOURCE_REMOTE);
        order.verify(userCredentialQueryApi).authenticationSubject("alice");
        order.verify(loginRateLimitService).attachAuthenticationSubject(
                permit, "alice", subject.value(), ClientIpResolver.SOURCE_REMOTE);
        order.verify(userCredentialQueryApi).prepareAuthentication("alice");
    }

    @Test
    void loginShouldNotLookUpTheAccountWhenAuthoritativeSubjectLeaseCannotBeAttached() {
        LoginRateLimitApplicationService.PasswordCheckPermit permit =
                new LoginRateLimitApplicationService.PasswordCheckPermit(uuid(86), List.of("permit"));
        UserCredentialQueryApi.AuthenticationSubject subject =
                new UserCredentialQueryApi.AuthenticationSubject("utf8mb4_unicode_ci:v1:subject-86");
        when(loginRateLimitService.acquirePasswordCheck(
                "alice", "127.0.0.1", ClientIpResolver.SOURCE_REMOTE)).thenReturn(permit);
        when(userCredentialQueryApi.authenticationSubject("alice")).thenReturn(subject);
        doThrow(new BusinessException(CommonErrorCode.TOO_MANY_REQUESTS))
                .when(loginRateLimitService).attachAuthenticationSubject(
                        permit, "alice", subject.value(), ClientIpResolver.SOURCE_REMOTE);

        assertThatThrownBy(() -> authService.login(loginCommand("alice", "secret", null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(CommonErrorCode.TOO_MANY_REQUESTS);

        verify(userCredentialQueryApi, never()).prepareAuthentication("alice");
        verify(loginRateLimitService).releasePasswordCheck(permit);
    }

    @Test
    void loginShouldReleaseTheProvisionalPermitWhenSubjectResolutionFails() {
        LoginRateLimitApplicationService.PasswordCheckPermit permit =
                new LoginRateLimitApplicationService.PasswordCheckPermit(uuid(85), List.of("permit"));
        when(loginRateLimitService.acquirePasswordCheck(
                "alice", "127.0.0.1", ClientIpResolver.SOURCE_REMOTE)).thenReturn(permit);
        when(userCredentialQueryApi.authenticationSubject("alice"))
                .thenThrow(new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> authService.login(loginCommand("alice", "secret", null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(CommonErrorCode.SERVICE_UNAVAILABLE);

        verify(userCredentialQueryApi, never()).prepareAuthentication("alice");
        verify(loginRateLimitService, never()).attachAuthenticationSubject(
                any(), anyString(), anyString(), anyString());
        verify(loginRateLimitService).releasePasswordCheck(permit);
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
                        LoginTokenIssuer.class,
                        RefreshTokenApplicationService.class,
                        LoginRateLimitApplicationService.class,
                        CaptchaChallengeComponent.class,
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
                .isEmpty();
    }

    @Test
    void loginShouldRecordFailureWhenCredentialsAreInvalid(CapturedOutput output) {
        LoginRateLimitApplicationService.PasswordCheckPermit permit =
                new LoginRateLimitApplicationService.PasswordCheckPermit(
                        UUID.fromString("00000000-0000-7000-8000-000000000088"),
                        List.of("permit"));
        when(loginRateLimitService.acquirePasswordCheck(
                "alice", "127.0.0.1", ClientIpResolver.SOURCE_REMOTE)).thenReturn(permit);
        when(userCredentialQueryApi.prepareAuthentication("alice"))
                .thenReturn(challenge(null, UserAuthenticationResultView.invalidCredentials()));

        Throwable thrown = catchThrowable(() -> authService.login(loginCommand("alice", "wrong-password", null, null)));

        assertThat(thrown).isInstanceOf(BusinessException.class);
        BusinessException error = (BusinessException) thrown;
        assertThat(error.getErrorCode()).isEqualTo(AuthErrorCode.INVALID_CREDENTIALS);
        verify(loginRateLimitService).recordFailure("alice", "127.0.0.1", ClientIpResolver.SOURCE_REMOTE);
        org.mockito.InOrder riskOrder = org.mockito.Mockito.inOrder(loginRateLimitService);
        riskOrder.verify(loginRateLimitService).acquirePasswordCheck(
                "alice", "127.0.0.1", ClientIpResolver.SOURCE_REMOTE);
        riskOrder.verify(loginRateLimitService).recordFailure(
                "alice", "127.0.0.1", ClientIpResolver.SOURCE_REMOTE);
        riskOrder.verify(loginRateLimitService).releasePasswordCheck(permit);
        verify(loginRateLimitService, never()).resetSubject(any());
        assertThat(output.getAll())
                .contains("community.reason_code=invalid_credentials")
                .contains("username=alice")
                .contains("source.ip=127.0.0.1")
                .doesNotContain("wrong-password");
    }

    @Test
    void loginShouldRecordFailureWhenUserIsDisabled(CapturedOutput output) {
        UserCredentialView disabledUser = new UserCredentialView(uuid(7), "alice", 0, 0, "h1", 0L, false, false);
        when(userCredentialQueryApi.prepareAuthentication("alice"))
                .thenReturn(challenge(null, UserAuthenticationResultView.userDisabled(disabledUser)));

        Throwable thrown = catchThrowable(() -> authService.login(loginCommand("alice", "secret", null, null)));

        assertThat(thrown).isInstanceOf(BusinessException.class);
        BusinessException error = (BusinessException) thrown;
        assertThat(error.getErrorCode()).isEqualTo(AuthErrorCode.USER_DISABLED);
        verify(loginRateLimitService).recordFailure("alice", "127.0.0.1", ClientIpResolver.SOURCE_REMOTE);
        verify(loginRateLimitService, never()).resetSubject(any());
        assertThat(output.getAll())
                .contains("community.reason_code=user_disabled")
                .contains("username=alice")
                .contains("source.ip=127.0.0.1")
                .doesNotContain("secret");
    }

    @Test
    void loginShouldResetOnlyUserRateLimitAfterSuccessfulAuthentication(CapturedOutput output) {
        UUID userId = uuid(7);
        UserCredentialView user = new UserCredentialView(userId, "alice", 1, 0, "h1", 0L, true, true);
        when(userCredentialQueryApi.prepareAuthentication("alice"))
                .thenReturn(challenge(null, UserAuthenticationResultView.authenticated(user)));

        RefreshCookieSpec cookie = issuedCookie("rt");
        when(userCredentialQueryApi.authoritiesOf(user)).thenReturn(List.of("ROLE_USER"));
        when(authTokenPort.createAccessToken(eq(userId), eq("alice"), eq(List.of("ROLE_USER")), eq(0L))).thenReturn("access-token");
        when(refreshTokenService.issue(userId, 0L)).thenReturn(new RefreshTokenApplicationService.IssuedRefreshToken("rt", cookie));

        LoginResult result = authService.login(loginCommand("alice", "secret", null, null));

        assertThat(result.accessToken()).isEqualTo("access-token");
        assertThat(result.refreshCookie()).isEqualTo(cookie);
        assertThat(result.refreshCookie().value()).isEqualTo("rt");
        verify(loginRateLimitService).resetSubject("alice");
        verify(loginRateLimitService, never()).recordFailure(any(), any(), any());
        assertThat(output.getAll())
                .contains("user.id=" + userId)
                .contains("username=alice")
                .contains("source.ip=127.0.0.1")
                .doesNotContain("secret")
                .doesNotContain("access-token");
    }

    @Test
    void loginShouldRecordDauSupplementAfterSuccessfulAuthentication() {
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UserCredentialView user = new UserCredentialView(userId, "alice", 1, 0, null, 0L, true, true);
        when(userCredentialQueryApi.prepareAuthentication("alice"))
                .thenReturn(challenge(null, UserAuthenticationResultView.authenticated(user)));
        when(userCredentialQueryApi.authoritiesOf(user)).thenReturn(List.of("ROLE_USER"));
        when(authTokenPort.createAccessToken(eq(userId), eq("alice"), anyList(), eq(0L))).thenReturn("access-token");
        when(refreshTokenService.issue(userId, 0L)).thenReturn(new RefreshTokenApplicationService.IssuedRefreshToken("refresh-token", issuedCookie("refresh-token")));

        authService.login(new LoginCommand("alice", "pw", null, null, "1.1.1.1", ClientIpResolver.SOURCE_REMOTE));

        verify(analyticsIngestService).recordLoginSuccess(userId);
    }

    @Test
    void loginShouldUseTheOwnerDerivedSubjectForCollationEquivalentUsername() {
        UUID userId = uuid(41);
        String subject = "utf8mb4_unicode_ci:v1:collation-subject";
        LoginRateLimitApplicationService.PasswordCheckPermit lookupPermit =
                new LoginRateLimitApplicationService.PasswordCheckPermit(uuid(44), List.of("lookup-permit"));
        UserCredentialQueryApi.AuthenticationChallenge challenge = challenge(
                userId,
                UserAuthenticationResultView.invalidCredentials()
        );
        when(loginRateLimitService.acquirePasswordCheck(
                "coeur", "127.0.0.1", ClientIpResolver.SOURCE_REMOTE)).thenReturn(lookupPermit);
        when(userCredentialQueryApi.authenticationSubject("coeur"))
                .thenReturn(new UserCredentialQueryApi.AuthenticationSubject(subject));
        when(userCredentialQueryApi.prepareAuthentication("coeur")).thenReturn(challenge);

        Throwable thrown = catchThrowable(() -> authService.login(
                loginCommand("coeur", "wrong-password", null, null)));

        assertThat(thrown).isInstanceOf(BusinessException.class);
        verify(loginRateLimitService).acquirePasswordCheck(
                "coeur", "127.0.0.1", ClientIpResolver.SOURCE_REMOTE);
        verify(loginRateLimitService).attachAuthenticationSubject(
                lookupPermit, "coeur", subject, ClientIpResolver.SOURCE_REMOTE);
        verify(loginRateLimitService).recordFailure(
                subject, "127.0.0.1", ClientIpResolver.SOURCE_REMOTE);
    }

    @Test
    void loginShouldReserveConcurrentBudgetBeforeIdentityLookupAndReleaseItOnLookupFailure() {
        LoginRateLimitApplicationService.PasswordCheckPermit lookupPermit =
                new LoginRateLimitApplicationService.PasswordCheckPermit(uuid(51), List.of("lookup-permit"));
        when(loginRateLimitService.acquirePasswordCheck(
                "alice", "127.0.0.1", ClientIpResolver.SOURCE_REMOTE)).thenReturn(lookupPermit);
        when(userCredentialQueryApi.prepareAuthentication("alice"))
                .thenThrow(new RuntimeException("user lookup unavailable"));

        assertThatThrownBy(() -> authService.login(loginCommand("alice", "secret", null, null)))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("user lookup unavailable");

        var order = org.mockito.Mockito.inOrder(loginRateLimitService, userCredentialQueryApi);
        order.verify(loginRateLimitService).acquirePasswordCheck(
                "alice", "127.0.0.1", ClientIpResolver.SOURCE_REMOTE);
        order.verify(userCredentialQueryApi).authenticationSubject("alice");
        order.verify(loginRateLimitService).attachAuthenticationSubject(
                lookupPermit, "alice", "alice", ClientIpResolver.SOURCE_REMOTE);
        order.verify(userCredentialQueryApi).prepareAuthentication("alice");
        order.verify(loginRateLimitService).releasePasswordCheck(lookupPermit);
    }

    @Test
    void loginShouldRejectUnsafeUsernameBeforeIdentityLookupOrPasswordCheck(CapturedOutput output) {
        Throwable thrown = catchThrowable(() -> authService.login(
                loginCommand("a\u200Dlice", "secret", null, null)));

        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(AuthErrorCode.INVALID_CREDENTIALS);
        verify(userCredentialQueryApi, never()).prepareAuthentication(anyString());
        verify(userCredentialQueryApi, never()).authenticationSubject(anyString());
        verify(loginRateLimitService).recordFailure(
                null, "127.0.0.1", ClientIpResolver.SOURCE_REMOTE);
        assertThat(output.getAll()).contains("username=a%200Dlice");
    }

    @Test
    void loginShouldFailClosedWhenPasswordCheckLeaseIsLost() {
        UUID userId = uuid(42);
        UserCredentialView user = new UserCredentialView(userId, "alice", 1, 0, "h1", 0L, true, true);
        LoginRateLimitApplicationService.PasswordCheckPermit permit =
                new LoginRateLimitApplicationService.PasswordCheckPermit(uuid(43), List.of("permit"));
        when(userCredentialQueryApi.prepareAuthentication("alice"))
                .thenReturn(challenge(userId, UserAuthenticationResultView.authenticated(user)));
        when(loginRateLimitService.acquirePasswordCheck(
                "alice", "127.0.0.1", ClientIpResolver.SOURCE_REMOTE)).thenReturn(permit);
        doThrow(new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE))
                .when(loginRateLimitService).assertPasswordCheckOwned(permit);

        Throwable thrown = catchThrowable(() -> authService.login(
                loginCommand("alice", "secret", null, null)));

        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(CommonErrorCode.SERVICE_UNAVAILABLE);
        verify(loginRateLimitService).releasePasswordCheck(permit);
        verify(authTokenPort, never()).createAccessToken(any(), anyString(), anyList(), anyLong());
    }

    @Test
    void loginShouldRejectNullCommand() {
        assertThatThrownBy(() -> authService.login(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("command must not be null");
    }

    @Test
    void loginShouldLogDeniedWhenCaptchaIsRequiredButMissing(CapturedOutput output) {
        when(loginRateLimitService.isCaptchaRequired(
                eq("alice"), eq("127.0.0.1"), any())).thenReturn(true);

        Throwable thrown = catchThrowable(() -> authService.login(loginCommand("alice", "secret", "cid", "")));

        assertThat(thrown).isInstanceOf(BusinessException.class);
        BusinessException error = (BusinessException) thrown;
        assertThat(error.getErrorCode()).isEqualTo(AuthErrorCode.CAPTCHA_REQUIRED);
        verify(loginRateLimitService).recordFailure("alice", "127.0.0.1", ClientIpResolver.SOURCE_REMOTE);
        assertThat(output.getAll())
                .contains("community.reason_code=captcha_required")
                .contains("username=alice")
                .contains("source.ip=127.0.0.1")
                .doesNotContain("secret")
                .doesNotContain("cid");
    }

    @Test
    void loginShouldLogDeniedWhenCaptchaIsInvalid(CapturedOutput output) {
        when(loginRateLimitService.isCaptchaRequired(
                eq("alice"), eq("127.0.0.1"), any())).thenReturn(true);
        when(captchaService.verify(CAPTCHA_ID, "bad-code")).thenReturn(false);

        Throwable thrown = catchThrowable(() -> authService.login(loginCommand("alice", "secret", CAPTCHA_ID, "bad-code")));

        assertThat(thrown).isInstanceOf(BusinessException.class);
        BusinessException error = (BusinessException) thrown;
        assertThat(error.getErrorCode()).isEqualTo(AuthErrorCode.CAPTCHA_INVALID);
        verify(loginRateLimitService).recordFailure("alice", "127.0.0.1", ClientIpResolver.SOURCE_REMOTE);
        assertThat(output.getAll())
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
        UserCredentialView user = new UserCredentialView(userId, "alice", 1, 0, "h1", 0L, true, true);
        when(userCredentialQueryApi.prepareAuthentication("alice"))
                .thenReturn(challenge(null, UserAuthenticationResultView.authenticated(user)));
        when(userCredentialQueryApi.authoritiesOf(user)).thenReturn(List.of("ROLE_USER"));
        when(authTokenPort.createAccessToken(eq(userId), eq("alice"), eq(List.of("ROLE_USER")), eq(0L))).thenReturn("access-token");
        when(refreshTokenService.issue(userId, 0L)).thenThrow(new RuntimeException("issue failed"));

        Throwable thrown = catchThrowable(() -> authService.login(loginCommand("alice", "secret", null, null)));

        assertThat(thrown).isInstanceOf(RuntimeException.class).hasMessage("issue failed");
        assertThat(output.getAll()).doesNotContain("event.category=security event.action=login event.outcome=success");
    }

    @Test
    void refreshShouldLetRefreshTokenServiceDecideInvalidTokenSoReplayDetectionCanRun() {
        when(refreshTokenService.beginRotation("replayed-token")).thenReturn(null);

        Throwable thrown = catchThrowable(() -> authService.refresh(new RefreshCommand("replayed-token")));

        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(AuthErrorCode.REFRESH_TOKEN_INVALID);
        verify(refreshTokenService).beginRotation("replayed-token");
        verify(refreshTokenService, never()).find("replayed-token");
        verify(userCredentialQueryApi, never()).getByUserId(any());
    }

    @Test
    void refreshShouldRejectNullCommand() {
        assertThatThrownBy(() -> authService.refresh(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("command must not be null");
    }

    @Test
    void refreshShouldValidateUserBeforeIssuingReplacementRefreshToken() {
        UUID userId = uuid(9);
        RefreshTokenRepository.StoredRefreshToken consumed =
                new RefreshTokenRepository.StoredRefreshToken("old-refresh", userId, "family-1", 0L, Instant.now().plusSeconds(600), ROTATION_LEASE_ID);
        UserCredentialView disabled = new UserCredentialView(userId, "alice", 0, 0, "h1", 0L, false, false);
        when(refreshTokenService.beginRotation("old-refresh")).thenReturn(consumed);
        when(userCredentialQueryApi.getByUserId(userId)).thenReturn(disabled);

        Throwable thrown = catchThrowable(() -> authService.refresh(new RefreshCommand("old-refresh")));

        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(AuthErrorCode.USER_DISABLED);
        verify(refreshTokenService, never()).generateReplacementToken(any(UUID.class), anyString());
        verify(refreshTokenService).revokeFamily("family-1");
    }

    @Test
    void refreshShouldRejectBannedUserAndRevokeRefreshFamily() {
        UUID userId = uuid(21);
        RefreshTokenRepository.StoredRefreshToken consumed =
                new RefreshTokenRepository.StoredRefreshToken("old-refresh", userId, "family-ban", 77L, Instant.now().plusSeconds(600), ROTATION_LEASE_ID);
        UserCredentialView banned = new UserCredentialView(userId, "alice", 1, 0, "h1", 77L, false, false);
        when(refreshTokenService.beginRotation("old-refresh")).thenReturn(consumed);
        when(userCredentialQueryApi.getByUserId(userId)).thenReturn(banned);

        Throwable thrown = catchThrowable(() -> authService.refresh(new RefreshCommand("old-refresh")));

        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(AuthErrorCode.USER_DISABLED);
        verify(refreshTokenService).revokeFamily("family-ban");
        verify(refreshTokenService, never()).generateReplacementToken(any(UUID.class), anyString());
    }

    @Test
    void refreshShouldMapMissingUserToUserDisabledAndRevokeRefreshFamily() {
        UUID userId = uuid(10);
        RefreshTokenRepository.StoredRefreshToken consumed =
                new RefreshTokenRepository.StoredRefreshToken("old-refresh", userId, "family-2", 0L, Instant.now().plusSeconds(600), ROTATION_LEASE_ID);
        when(refreshTokenService.beginRotation("old-refresh")).thenReturn(consumed);
        when(userCredentialQueryApi.getByUserId(userId)).thenReturn(null);

        Throwable thrown = catchThrowable(() -> authService.refresh(new RefreshCommand("old-refresh")));

        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(AuthErrorCode.USER_DISABLED);
        verify(refreshTokenService, never()).generateReplacementToken(any(UUID.class), anyString());
        verify(refreshTokenService).revokeFamily("family-2");
    }

    @Test
    void refreshShouldRevokeFamilyWhenSecurityVersionChangedAfterTokenWasIssued() {
        UUID userId = uuid(12);
        RefreshTokenRepository.StoredRefreshToken pending = new RefreshTokenRepository.StoredRefreshToken(
                "old-refresh",
                userId,
                "family-security-version",
                41L,
                Instant.now().plusSeconds(600),
                ROTATION_LEASE_ID
        );
        UserCredentialView currentCredential = new UserCredentialView(
                userId,
                "alice",
                1,
                0,
                "h1",
                42L,
                true,
                true
        );
        when(refreshTokenService.beginRotation("old-refresh")).thenReturn(pending);
        when(userCredentialQueryApi.getByUserId(userId)).thenReturn(currentCredential);

        Throwable thrown = catchThrowable(() -> authService.refresh(new RefreshCommand("old-refresh")));

        assertThat(thrown).isInstanceOf(RefreshFailure.class);
        RefreshFailure failure = (RefreshFailure) thrown;
        assertThat(failure.getErrorCode()).isEqualTo(AuthErrorCode.REFRESH_TOKEN_INVALID);
        verify(refreshTokenService).revokeFamily("family-security-version");
        verify(refreshTokenService, never()).generateReplacementToken(any(UUID.class), anyString());
        verify(refreshTokenService, never()).finishRotation(
                anyString(),
                anyString(),
                any(UUID.class),
                anyString(),
                anyLong(),
                any(UUID.class)
        );
        verify(authTokenPort, never()).createAccessToken(any(UUID.class), anyString(), anyList(), anyLong());
    }

    @Test
    void refreshShouldIssueReplacementOnlyAfterUserIsActive() {
        UUID userId = uuid(11);
        UserCredentialView user = new UserCredentialView(userId, "alice", 1, 0, "h1", 0L, true, true);
        RefreshCookieSpec cookie = issuedCookie("new-refresh");
        RefreshTokenRepository.StoredRefreshToken consumed =
                new RefreshTokenRepository.StoredRefreshToken("old-refresh", userId, "family-3", 0L, Instant.now().plusSeconds(600), ROTATION_LEASE_ID);
        when(refreshTokenService.beginRotation("old-refresh")).thenReturn(consumed);
        when(userCredentialQueryApi.getByUserId(userId)).thenReturn(user);
        when(userCredentialQueryApi.authoritiesOf(user)).thenReturn(List.of("ROLE_USER"));
        when(authTokenPort.createAccessToken(userId, "alice", List.of("ROLE_USER"), 0L)).thenReturn("access-token");
        when(refreshTokenService.generateReplacementToken(userId, "family-3"))
                .thenReturn(new RefreshTokenApplicationService.IssuedRefreshToken("new-refresh", cookie));
        when(refreshTokenService.finishRotation(
                "old-refresh", "new-refresh", userId, "family-3", 0L, ROTATION_LEASE_ID
        )).thenReturn(true);

        RefreshResult result = authService.refresh(new RefreshCommand("old-refresh"));

        assertThat(result.accessToken()).isEqualTo("access-token");
        assertThat(result.refreshCookie()).isEqualTo(cookie);
        verify(refreshTokenService).generateReplacementToken(userId, "family-3");
        verify(refreshTokenService).finishRotation(
                "old-refresh", "new-refresh", userId, "family-3", 0L, ROTATION_LEASE_ID
        );
        verify(refreshTokenService, never()).find("new-refresh");
    }

    @Test
    void refreshShouldReturnServiceUnavailableAndKeepFamilyWhenRollbackSucceeds() {
        UUID userId = uuid(31);
        RefreshTokenRepository.StoredRefreshToken pending =
                new RefreshTokenRepository.StoredRefreshToken("old-refresh", userId, "family-rollback", 0L, Instant.now().plusSeconds(600), ROTATION_LEASE_ID);
        when(refreshTokenService.beginRotation("old-refresh")).thenReturn(pending);
        when(userCredentialQueryApi.getByUserId(userId)).thenThrow(new RuntimeException("user api down"));
        when(refreshTokenService.rollbackPendingRotation("old-refresh", ROTATION_LEASE_ID)).thenReturn(true);

        Throwable thrown = catchThrowable(() -> authService.refresh(new RefreshCommand("old-refresh")));

        assertThat(thrown).isInstanceOf(RefreshFailure.class);
        RefreshFailure failure = (RefreshFailure) thrown;
        assertThat(failure.getErrorCode()).isEqualTo(CommonErrorCode.SERVICE_UNAVAILABLE);
        verify(refreshTokenService, never()).revokeFamily("family-rollback");
    }

    @Test
    void logoutShouldRejectNullCommand() {
        assertThatThrownBy(() -> authService.logout(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("command must not be null");
    }

    @Test
    void logoutShouldRevokeRefreshTokenFamilyWhenTokenPresent() {
        authService.logout(new LogoutCommand("refresh-token"));

        verify(refreshTokenService).revokeFamilyByToken("refresh-token");
    }

    @Test
    void refreshShouldRevokeFamilyWhenRollbackFails() {
        UUID userId = uuid(32);
        RefreshTokenRepository.StoredRefreshToken pending =
                new RefreshTokenRepository.StoredRefreshToken("old-refresh", userId, "family-fail-closed", 0L, Instant.now().plusSeconds(600), ROTATION_LEASE_ID);
        when(refreshTokenService.beginRotation("old-refresh")).thenReturn(pending);
        when(userCredentialQueryApi.getByUserId(userId)).thenThrow(new RuntimeException("user api down"));
        when(refreshTokenService.rollbackPendingRotation("old-refresh", ROTATION_LEASE_ID)).thenReturn(false);

        Throwable thrown = catchThrowable(() -> authService.refresh(new RefreshCommand("old-refresh")));

        assertThat(thrown).isInstanceOf(RefreshFailure.class);
        RefreshFailure failure = (RefreshFailure) thrown;
        assertThat(failure.getErrorCode()).isEqualTo(CommonErrorCode.SERVICE_UNAVAILABLE);
        verify(refreshTokenService).revokeFamily("family-fail-closed");
    }

    @Test
    void loginShouldEncodeUnsafeCharactersInSecurityEventTokens(CapturedOutput output) {
        String spoofedUsername = "alice bob=\nroot";
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
        when(userCredentialQueryApi.prepareAuthentication("alice"))
                .thenReturn(challenge(null, UserAuthenticationResultView.invalidCredentials()));

        Throwable thrown = catchThrowable(() -> authService.login(loginCommand("alice", "wrong-password", null, null)));

        assertThat(thrown).isInstanceOf(BusinessException.class);

        JsonNode event = findJsonEvent(output);
        assertThat(event.path("service.name").asText()).isEqualTo("community-app");
        assertThat(event.path("service.version").asText()).isEqualTo(SERVICE_VERSION);
        assertThat(event.path("service.namespace").asText()).isEqualTo("community");
        assertThat(event.path("deployment.environment").asText()).isEqualTo("test");
        assertThat(event.has("traceId")).isFalse();
        assertThat(event.path("event.category").asText()).isEqualTo("security");
        assertThat(event.path("event.action").asText()).isEqualTo("login");
        assertThat(event.path("event.outcome").asText()).isEqualTo("denied");
        assertThat(event.path("message").asText())
                .contains("community.reason_code=invalid_credentials")
                .contains("source.ip=127.0.0.1");
    }

    private void initializeProductionLogging(String serviceName) {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("spring.application.name", serviceName);
        environment.setProperty("community.logging.service-version", SERVICE_VERSION);
        environment.setProperty("community.logging.deployment-environment", "test");
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

    private UserCredentialQueryApi.AuthenticationChallenge challenge(
            UUID userId,
            UserAuthenticationResultView result
    ) {
        return new UserCredentialQueryApi.AuthenticationChallenge() {
            @Override
            public UUID userId() {
                return userId;
            }

            @Override
            public UserAuthenticationResultView authenticate(String password) {
                return result;
            }
        };
    }
}
