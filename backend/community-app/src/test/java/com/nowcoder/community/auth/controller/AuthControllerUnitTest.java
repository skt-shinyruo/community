package com.nowcoder.community.auth.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.auth.application.CaptchaApplicationService;
import com.nowcoder.community.auth.application.CaptchaApplicationService.CaptchaIssueResult;
import com.nowcoder.community.auth.application.CaptchaApplicationService.IssueCaptchaCommand;
import com.nowcoder.community.auth.application.LoginApplicationService;
import com.nowcoder.community.auth.application.LoginApplicationService.LoginCommand;
import com.nowcoder.community.auth.application.LoginApplicationService.LogoutCommand;
import com.nowcoder.community.auth.application.LoginApplicationService.RefreshCommand;
import com.nowcoder.community.auth.application.LoginApplicationService.RefreshResult;
import com.nowcoder.community.auth.application.PasswordResetApplicationService;
import com.nowcoder.community.auth.application.PasswordResetApplicationService.PasswordResetRequestResult;
import com.nowcoder.community.auth.application.PasswordResetApplicationService.RequestPasswordResetCommand;
import com.nowcoder.community.auth.application.RegistrationApplicationService;
import com.nowcoder.community.auth.application.RegistrationApplicationService.RegisterCommand;
import com.nowcoder.community.auth.application.RegistrationApplicationService.RegisterResult;
import com.nowcoder.community.auth.application.RegistrationVerificationApplicationService;
import com.nowcoder.community.auth.application.RegistrationVerificationApplicationService.RegisterCodeResendResult;
import com.nowcoder.community.auth.application.RegistrationVerificationApplicationService.ResendRegisterCodeCommand;
import com.nowcoder.community.auth.application.RegistrationVerificationApplicationService.VerifyRegisterCodeCommand;
import com.nowcoder.community.auth.application.result.LoginResult;
import com.nowcoder.community.auth.application.LoginApplicationService.RefreshFailure;
import com.nowcoder.community.auth.application.result.RefreshCookieSpec;
import com.nowcoder.community.auth.controller.dto.LoginRequest;
import com.nowcoder.community.auth.controller.dto.LoginResponse;
import com.nowcoder.community.auth.controller.dto.MeResponse;
import com.nowcoder.community.auth.controller.dto.PasswordResetConfirmRequest;
import com.nowcoder.community.auth.controller.dto.PasswordResetRequestRequest;
import com.nowcoder.community.auth.controller.dto.RegisterCodeResendRequest;
import com.nowcoder.community.auth.controller.dto.RegisterCodeVerifyRequest;
import com.nowcoder.community.auth.controller.dto.RegisterRequest;
import com.nowcoder.community.auth.exception.AuthErrorCode;
import com.nowcoder.community.common.constants.ValidationLimits;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.exception.CommonErrorCode;
import com.nowcoder.community.common.json.JsonMappers;
import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.common.web.net.ClientIpResolver;
import jakarta.servlet.http.Cookie;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerUnitTest {

    private static final ObjectMapper OBJECT_MAPPER = JsonMappers.standard();

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Mock
    private LoginApplicationService loginApplicationService;

    @Mock
    private RegistrationApplicationService registrationApplicationService;

    @Mock
    private RegistrationVerificationApplicationService registrationVerificationApplicationService;

    @Mock
    private CaptchaApplicationService captchaApplicationService;

    @Mock
    private PasswordResetApplicationService passwordResetApplicationService;

    @Mock
    private ClientIpResolver clientIpResolver;

    private AuthController controller;

    @BeforeEach
    void setUp() {
        lenient().when(loginApplicationService.refreshCookieName()).thenReturn("refresh_token");
        lenient().when(clientIpResolver.resolve(any())).thenReturn(new ClientIpResolver.ResolvedClientIp("127.0.0.1", ClientIpResolver.SOURCE_REMOTE));
        controller = new AuthController(
                loginApplicationService,
                registrationApplicationService,
                registrationVerificationApplicationService,
                captchaApplicationService,
                passwordResetApplicationService,
                clientIpResolver
        );
    }

    @Test
    void authResponsesShouldKeepTheirWireFieldSets() {
        when(loginApplicationService.login(any(LoginCommand.class)))
                .thenReturn(new LoginResult("access-token", issuedCookie("refresh-token")));
        LoginRequest loginRequest = new LoginRequest("alice", "secret", null, null);
        assertDataFields(
                controller.login(loginRequest, new MockHttpServletRequest(), new MockHttpServletResponse()),
                "accessToken"
        );

        when(captchaApplicationService.issue(any(IssueCaptchaCommand.class)))
                .thenReturn(new CaptchaIssueResult("captcha-id", "image", 60));
        assertDataFields(
                controller.captcha(new MockHttpServletRequest(), new MockHttpServletResponse()),
                "captchaId", "imageBase64", "ttlSeconds"
        );

        when(registrationApplicationService.register(any(RegisterCommand.class)))
                .thenReturn(new RegisterResult(
                        UUID.fromString("00000000-0000-7000-8000-000000000007"),
                        "registration-token",
                        true,
                        "a***@example.com",
                        "123456"
                ));
        RegisterRequest registerRequest = new RegisterRequest(
                "alice", "secret", "alice@example.com", null, null);
        assertDataFields(
                controller.register(registerRequest, new MockHttpServletRequest()),
                "userId", "registrationToken", "emailCodeIssued", "maskedEmail", "debugEmailCode"
        );

        when(registrationVerificationApplicationService.resendCode(any(ResendRegisterCodeCommand.class)))
                .thenReturn(new RegisterCodeResendResult(true, "a***@example.com", "123456"));
        RegisterCodeResendRequest resendRequest = new RegisterCodeResendRequest(
                "registration-token", null, null);
        assertDataFields(
                controller.resendRegisterCode(resendRequest, new MockHttpServletRequest()),
                "issued", "maskedEmail", "debugEmailCode"
        );

        when(passwordResetApplicationService.requestReset(any(RequestPasswordResetCommand.class)))
                .thenReturn(new PasswordResetRequestResult(true));
        PasswordResetRequestRequest resetRequest = new PasswordResetRequestRequest(
                "alice@example.com", null, null);
        assertDataFields(
                controller.requestPasswordReset(resetRequest, new MockHttpServletRequest()),
                "issued"
        );
    }

    @Test
    void loginShouldSetRefreshCookieAndReturnAccessToken() {
        LoginRequest req = new LoginRequest("u", "p", null, null);

        RefreshCookieSpec refreshCookie = issuedCookie("rt", true);

        when(loginApplicationService.login(any(LoginCommand.class)))
                .thenReturn(new LoginResult("at", refreshCookie));

        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        MockHttpServletResponse httpResponse = new MockHttpServletResponse();

        Result<LoginResponse> resp = controller.login(req, httpRequest, httpResponse);
        assertThat(resp.getCode()).isEqualTo(0);
        assertThat(resp.getData()).isNotNull();
        assertThat(resp.getData().accessToken()).isEqualTo("at");

        String setCookie = httpResponse.getHeader(HttpHeaders.SET_COOKIE);
        assertIssuedRefreshCookie(setCookie, "rt", true);
        verify(loginApplicationService).login(new LoginCommand("u", "p", null, null, "127.0.0.1", ClientIpResolver.SOURCE_REMOTE));
    }

    @Test
    void refreshShouldSetRefreshCookieAndReturnAccessToken() {
        RefreshCookieSpec refreshCookie = issuedCookie("rt2");

        when(loginApplicationService.refresh(any(RefreshCommand.class)))
                .thenReturn(new RefreshResult("at2", refreshCookie));

        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        httpRequest.setCookies(new Cookie("refresh_token", "presented-token"));
        MockHttpServletResponse httpResponse = new MockHttpServletResponse();

        Result<LoginResponse> resp = controller.refresh(httpRequest, httpResponse);
        assertThat(resp.getCode()).isEqualTo(0);
        assertThat(resp.getData()).isNotNull();
        assertThat(resp.getData().accessToken()).isEqualTo("at2");

        String setCookie = httpResponse.getHeader(HttpHeaders.SET_COOKIE);
        assertIssuedRefreshCookie(setCookie, "rt2", false);
        verify(loginApplicationService).refresh(new RefreshCommand("presented-token"));
    }

    @Test
    void refreshShouldPreserveRefreshCookieWhenTokenIsInvalid() {
        when(loginApplicationService.refresh(any(RefreshCommand.class)))
                .thenThrow(new BusinessException(AuthErrorCode.REFRESH_TOKEN_INVALID));

        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        MockHttpServletResponse httpResponse = new MockHttpServletResponse();

        Throwable thrown = catchThrowable(() -> controller.refresh(httpRequest, httpResponse));

        assertThat(thrown).isInstanceOf(BusinessException.class);
        verify(loginApplicationService, never()).clearRefreshCookie();
        assertThat(httpResponse.getHeader(HttpHeaders.SET_COOKIE)).isNull();
    }

    @Test
    void refreshShouldPreserveRefreshCookieWhenUserIsDisabled() {
        when(loginApplicationService.refresh(any(RefreshCommand.class)))
                .thenThrow(new BusinessException(AuthErrorCode.USER_DISABLED));

        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        MockHttpServletResponse httpResponse = new MockHttpServletResponse();

        Throwable thrown = catchThrowable(() -> controller.refresh(httpRequest, httpResponse));

        assertThat(thrown).isInstanceOf(BusinessException.class);
        verify(loginApplicationService, never()).clearRefreshCookie();
        assertThat(httpResponse.getHeader(HttpHeaders.SET_COOKIE)).isNull();
    }

    @Test
    void refreshShouldPreserveRefreshCookieWhenApplicationFails() {
        when(loginApplicationService.refresh(any(RefreshCommand.class)))
                .thenThrow(new RefreshFailure(CommonErrorCode.SERVICE_UNAVAILABLE));

        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        MockHttpServletResponse httpResponse = new MockHttpServletResponse();

        Throwable thrown = catchThrowable(() -> controller.refresh(httpRequest, httpResponse));

        assertThat(thrown).isInstanceOf(RefreshFailure.class);
        verify(loginApplicationService, never()).clearRefreshCookie();
        assertThat(httpResponse.getHeader(HttpHeaders.SET_COOKIE)).isNull();
    }

    @Test
    void logoutShouldClearRefreshCookie() {
        RefreshCookieSpec clearCookie = clearedCookie();

        when(loginApplicationService.clearRefreshCookie()).thenReturn(clearCookie);

        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        httpRequest.setCookies(new Cookie("refresh_token", "logout-token"));
        MockHttpServletResponse httpResponse = new MockHttpServletResponse();

        controller.logout(httpRequest, httpResponse);

        verify(loginApplicationService).logout(new LogoutCommand("logout-token"));
        assertClearedRefreshCookie(httpResponse.getHeader(HttpHeaders.SET_COOKIE));
    }

    @Test
    void logoutShouldClearRefreshCookieAndPropagateDurableRevokeFailure() {
        RefreshCookieSpec clearCookie = clearedCookie();
        RuntimeException durableFailure = new IllegalStateException("database unavailable");
        doThrow(durableFailure).when(loginApplicationService).logout(new LogoutCommand("logout-token"));
        when(loginApplicationService.clearRefreshCookie()).thenReturn(clearCookie);

        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        httpRequest.setCookies(new Cookie("refresh_token", "logout-token"));
        MockHttpServletResponse httpResponse = new MockHttpServletResponse();

        Throwable thrown = catchThrowable(() -> controller.logout(httpRequest, httpResponse));

        assertThat(thrown).isSameAs(durableFailure);
        verify(loginApplicationService).clearRefreshCookie();
        assertClearedRefreshCookie(httpResponse.getHeader(HttpHeaders.SET_COOKIE));
    }

    @Test
    void meShouldReadSubjectAndClaimsFromJwt() {
        UUID userId = UUID.fromString("00000000-0000-7000-8000-000000000042");
        Jwt jwt = Jwt.withTokenValue("t")
                .header("alg", "none")
                .subject(userId.toString())
                .claim("username", "u42")
                .claim("authorities", List.of("ROLE_USER"))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();

        Authentication authentication = org.mockito.Mockito.mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(jwt);

        Result<MeResponse> resp = controller.me(authentication);
        assertThat(resp.getCode()).isEqualTo(0);
        assertThat(resp.getData()).isNotNull();
        assertThat(resp.getData().userId()).isEqualTo(userId);
        assertThat(resp.getData().username()).isEqualTo("u42");
        assertThat(resp.getData().authorities()).contains("ROLE_USER");
    }

    @Test
    void meShouldRejectNonUuidSubject() {
        Jwt jwt = Jwt.withTokenValue("t")
                .header("alg", "none")
                .subject("not-a-uuid")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();

        Authentication authentication = org.mockito.Mockito.mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(jwt);

        assertThatThrownBy(() -> controller.me(authentication))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(AuthErrorCode.TOKEN_INVALID);
    }

    @Test
    void captchaShouldSetNoCacheHeaders() {
        when(captchaApplicationService.issue(any(IssueCaptchaCommand.class))).thenReturn(new CaptchaIssueResult("cid", "img", 60));

        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        MockHttpServletResponse httpResponse = new MockHttpServletResponse();
        Result<CaptchaIssueResult> resp = controller.captcha(httpRequest, httpResponse);

        assertThat(httpResponse.getHeader(HttpHeaders.CACHE_CONTROL)).contains("no-store");
        assertThat(httpResponse.getHeader(HttpHeaders.PRAGMA)).contains("no-cache");
        assertThat(resp.getCode()).isEqualTo(0);
        assertThat(resp.getData()).isNotNull();
        assertThat(resp.getData().captchaId()).isEqualTo("cid");
    }

    @Test
    void captchaShouldPassClientIpToApplicationCommand() {
        when(captchaApplicationService.issue(any(IssueCaptchaCommand.class))).thenReturn(new CaptchaIssueResult("cid", "img", 60));

        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        MockHttpServletResponse httpResponse = new MockHttpServletResponse();
        controller.captcha(httpRequest, httpResponse);

        verify(captchaApplicationService).issue(new IssueCaptchaCommand("127.0.0.1"));
    }

    @Test
    void registerShouldReturnNewRegisterResponseContract() {
        UUID userId = UUID.fromString("00000000-0000-7000-8000-000000000007");
        RegisterRequest request = new RegisterRequest(
                "alice", "secret", "alice@example.com", "cid", "abcd");

        when(registrationApplicationService.register(any(RegisterCommand.class))).thenReturn(new RegisterResult(
                userId,
                "0123456789abcdef0123456789abcdef",
                true,
                "a***@example.com",
                "123456"
        ));

        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        Result<RegisterResult> response = controller.register(request, httpRequest);

        assertThat(response.getCode()).isEqualTo(0);
        assertThat(response.getData()).isNotNull();
        assertThat(response.getData().registrationToken()).isEqualTo("0123456789abcdef0123456789abcdef");
        assertThat(response.getData().emailCodeIssued()).isTrue();
        assertThat(response.getData().maskedEmail()).isEqualTo("a***@example.com");
        assertThat(response.getData().debugEmailCode()).isEqualTo("123456");
        verify(registrationApplicationService).register(new RegisterCommand(
                "alice", "secret", "alice@example.com", "cid", "abcd", "127.0.0.1"));
    }

    @Test
    void registerDtosShouldUseValidationLimitsForSizedFields() throws Exception {
        assertMaxSize(RegisterRequest.class, "captchaId", ValidationLimits.CAPTCHA_ID_MAX);
        assertMaxSize(RegisterRequest.class, "captchaCode", ValidationLimits.CAPTCHA_CODE_MAX);
        assertMaxSize(RegisterCodeResendRequest.class, "registrationToken", ValidationLimits.REGISTRATION_TOKEN_MAX);
        assertMaxSize(RegisterCodeResendRequest.class, "captchaId", ValidationLimits.CAPTCHA_ID_MAX);
        assertMaxSize(RegisterCodeResendRequest.class, "captchaCode", ValidationLimits.CAPTCHA_CODE_MAX);
        assertMaxSize(RegisterCodeVerifyRequest.class, "registrationToken", ValidationLimits.REGISTRATION_TOKEN_MAX);
        assertMaxSize(RegisterCodeVerifyRequest.class, "code", ValidationLimits.REGISTRATION_CODE_MAX);
        assertMaxSize(PasswordResetRequestRequest.class, "captchaId", ValidationLimits.CAPTCHA_ID_MAX);
        assertMaxSize(PasswordResetRequestRequest.class, "captchaCode", ValidationLimits.CAPTCHA_CODE_MAX);
        assertMaxSize(PasswordResetConfirmRequest.class, "resetToken", ValidationLimits.TOKEN_MAX);
        assertMaxSize(PasswordResetConfirmRequest.class, "captchaId", ValidationLimits.CAPTCHA_ID_MAX);
        assertMaxSize(PasswordResetConfirmRequest.class, "captchaCode", ValidationLimits.CAPTCHA_CODE_MAX);
    }

    @Test
    void registerCodeVerifyRequestShouldRejectOversizedRegistrationToken() {
        RegisterCodeVerifyRequest request = new RegisterCodeVerifyRequest(
                "x".repeat(ValidationLimits.REGISTRATION_TOKEN_MAX + 1), "123456");

        assertThat(validator.validate(request))
                .anySatisfy(violation -> assertThat(violation.getPropertyPath().toString()).isEqualTo("registrationToken"));
    }

    @Test
    void registerCodeResendRequestShouldRejectOversizedCaptchaFields() {
        RegisterCodeResendRequest request = new RegisterCodeResendRequest(
                "token",
                "c".repeat(ValidationLimits.CAPTCHA_ID_MAX + 1),
                "9".repeat(ValidationLimits.CAPTCHA_CODE_MAX + 1));

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("captchaId", "captchaCode");
    }

    @Test
    void resendRegisterCodeShouldReturnResponse() {
        RegisterCodeResendRequest request = new RegisterCodeResendRequest("token", "cid", "abcd");

        when(registrationVerificationApplicationService.resendCode(any(ResendRegisterCodeCommand.class)))
                .thenReturn(new RegisterCodeResendResult(true, "a***@example.com", "123456"));

        Result<RegisterCodeResendResult> response = controller.resendRegisterCode(
                request, new MockHttpServletRequest());

        assertThat(response.getCode()).isEqualTo(0);
        assertThat(response.getData()).isNotNull();
        assertThat(response.getData().issued()).isTrue();
        assertThat(response.getData().maskedEmail()).isEqualTo("a***@example.com");
        assertThat(response.getData().debugEmailCode()).isEqualTo("123456");
        verify(registrationVerificationApplicationService).resendCode(
                new ResendRegisterCodeCommand("token", "cid", "abcd", "127.0.0.1"));
    }

    @Test
    void verifyRegisterCodeShouldSetRefreshCookieAndReturnAccessToken() {
        RegisterCodeVerifyRequest request = new RegisterCodeVerifyRequest("token", "123456");

        RefreshCookieSpec refreshCookie = issuedCookie("rt3");

        when(registrationVerificationApplicationService.verifyAndLogin(any(VerifyRegisterCodeCommand.class)))
                .thenReturn(new LoginResult("at3", refreshCookie));

        MockHttpServletResponse httpResponse = new MockHttpServletResponse();
        Result<LoginResponse> response = controller.verifyRegisterCode(request, httpResponse);

        assertThat(response.getCode()).isEqualTo(0);
        assertThat(response.getData()).isNotNull();
        assertThat(response.getData().accessToken()).isEqualTo("at3");
        assertIssuedRefreshCookie(httpResponse.getHeader(HttpHeaders.SET_COOKIE), "rt3", false);
    }

    private static RefreshCookieSpec issuedCookie(String value) {
        return issuedCookie(value, false);
    }

    private static RefreshCookieSpec issuedCookie(String value, boolean secure) {
        return new RefreshCookieSpec(
                "refresh_token",
                value,
                true,
                secure,
                "/api/auth",
                "Lax",
                600
        );
    }

    private static RefreshCookieSpec clearedCookie() {
        return new RefreshCookieSpec(
                "refresh_token",
                "",
                true,
                false,
                "/api/auth",
                "Lax",
                0
        );
    }

    private static void assertIssuedRefreshCookie(String setCookie, String value, boolean secure) {
        assertThat(setCookie).isNotBlank();
        assertThat(setCookie).contains(
                "refresh_token=" + value,
                "Path=/api/auth",
                "Max-Age=600",
                "HttpOnly",
                "SameSite=Lax"
        );
        if (secure) {
            assertThat(setCookie).contains("Secure");
        } else {
            assertThat(setCookie).doesNotContain("Secure");
        }
    }

    private static void assertClearedRefreshCookie(String setCookie) {
        assertThat(setCookie).isNotBlank();
        assertThat(setCookie).contains(
                "refresh_token=",
                "Path=/api/auth",
                "Max-Age=0",
                "HttpOnly",
                "SameSite=Lax"
        );
        assertThat(setCookie).doesNotContain("Secure");
    }

    private static void assertMaxSize(Class<?> type, String fieldName, int expected) throws Exception {
        Field field = type.getDeclaredField(fieldName);
        Size size = field.getAnnotation(Size.class);
        assertThat(size).isNotNull();
        assertThat(size.max()).isEqualTo(expected);
    }

    private static void assertDataFields(Result<?> result, String... expectedFields) {
        JsonNode data = OBJECT_MAPPER.valueToTree(result).path("data");
        Set<String> actualFields = new LinkedHashSet<>();
        data.fieldNames().forEachRemaining(actualFields::add);
        assertThat(actualFields).containsExactlyInAnyOrder(expectedFields);
    }
}
