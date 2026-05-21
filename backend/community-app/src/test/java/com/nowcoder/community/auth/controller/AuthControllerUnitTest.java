package com.nowcoder.community.auth.controller;

import com.nowcoder.community.auth.application.CaptchaApplicationService;
import com.nowcoder.community.auth.application.LoginApplicationService;
import com.nowcoder.community.auth.application.PasswordResetApplicationService;
import com.nowcoder.community.auth.application.RegistrationApplicationService;
import com.nowcoder.community.auth.application.RegistrationVerificationApplicationService;
import com.nowcoder.community.auth.application.command.IssueCaptchaCommand;
import com.nowcoder.community.auth.application.command.LoginCommand;
import com.nowcoder.community.auth.application.command.LogoutCommand;
import com.nowcoder.community.auth.application.command.RefreshCommand;
import com.nowcoder.community.auth.application.command.RegisterCommand;
import com.nowcoder.community.auth.application.command.ResendRegisterCodeCommand;
import com.nowcoder.community.auth.application.command.VerifyRegisterCodeCommand;
import com.nowcoder.community.auth.application.result.CaptchaIssueResult;
import com.nowcoder.community.auth.application.result.LoginResult;
import com.nowcoder.community.auth.application.result.RefreshResult;
import com.nowcoder.community.auth.application.result.RefreshCookieSpec;
import com.nowcoder.community.auth.application.result.RegisterCodeResendResult;
import com.nowcoder.community.auth.application.result.RegisterResult;
import com.nowcoder.community.auth.controller.dto.LoginRequest;
import com.nowcoder.community.auth.controller.dto.LoginResponse;
import com.nowcoder.community.auth.controller.dto.MeResponse;
import com.nowcoder.community.auth.controller.dto.CaptchaIssueResponse;
import com.nowcoder.community.auth.controller.dto.RegisterCodeResendRequest;
import com.nowcoder.community.auth.controller.dto.RegisterCodeResendResponse;
import com.nowcoder.community.auth.controller.dto.RegisterCodeVerifyRequest;
import com.nowcoder.community.auth.controller.dto.RegisterRequest;
import com.nowcoder.community.auth.controller.dto.RegisterResponse;
import com.nowcoder.community.auth.exception.AuthErrorCode;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.common.web.net.ClientIpResolver;
import jakarta.servlet.http.Cookie;
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

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerUnitTest {

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
    void loginShouldSetRefreshCookieAndReturnAccessToken() {
        LoginRequest req = new LoginRequest();
        req.setUsername("u");
        req.setPassword("p");

        RefreshCookieSpec refreshCookie = issuedCookie("rt", true);

        when(loginApplicationService.login(any(LoginCommand.class)))
                .thenReturn(new LoginResult("at", refreshCookie));

        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        MockHttpServletResponse httpResponse = new MockHttpServletResponse();

        Result<LoginResponse> resp = controller.login(req, httpRequest, httpResponse);
        assertThat(resp.getCode()).isEqualTo(0);
        assertThat(resp.getData()).isNotNull();
        assertThat(resp.getData().getAccessToken()).isEqualTo("at");

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
        assertThat(resp.getData().getAccessToken()).isEqualTo("at2");

        String setCookie = httpResponse.getHeader(HttpHeaders.SET_COOKIE);
        assertIssuedRefreshCookie(setCookie, "rt2", false);
        verify(loginApplicationService).refresh(new RefreshCommand("presented-token"));
    }

    @Test
    void refreshShouldClearRefreshCookieWhenTokenIsInvalid() {
        RefreshCookieSpec clearCookie = clearedCookie();

        when(loginApplicationService.refresh(any(RefreshCommand.class)))
                .thenThrow(new BusinessException(AuthErrorCode.REFRESH_TOKEN_INVALID));
        when(loginApplicationService.clearRefreshCookie()).thenReturn(clearCookie);

        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        MockHttpServletResponse httpResponse = new MockHttpServletResponse();

        Throwable thrown = catchThrowable(() -> controller.refresh(httpRequest, httpResponse));

        assertThat(thrown).isInstanceOf(BusinessException.class);
        verify(loginApplicationService).clearRefreshCookie();
        assertClearedRefreshCookie(httpResponse.getHeader(HttpHeaders.SET_COOKIE));
    }

    @Test
    void refreshShouldClearRefreshCookieWhenUserIsDisabled() {
        RefreshCookieSpec clearCookie = clearedCookie();

        when(loginApplicationService.refresh(any(RefreshCommand.class)))
                .thenThrow(new BusinessException(AuthErrorCode.USER_DISABLED));
        when(loginApplicationService.clearRefreshCookie()).thenReturn(clearCookie);

        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        MockHttpServletResponse httpResponse = new MockHttpServletResponse();

        Throwable thrown = catchThrowable(() -> controller.refresh(httpRequest, httpResponse));

        assertThat(thrown).isInstanceOf(BusinessException.class);
        verify(loginApplicationService).clearRefreshCookie();
        assertClearedRefreshCookie(httpResponse.getHeader(HttpHeaders.SET_COOKIE));
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
        assertThat(resp.getData().getUserId()).isEqualTo(userId);
        assertThat(resp.getData().getUsername()).isEqualTo("u42");
        assertThat(resp.getData().getAuthorities()).contains("ROLE_USER");
    }

    @Test
    void captchaShouldSetNoCacheHeaders() {
        when(captchaApplicationService.issue(any(IssueCaptchaCommand.class))).thenReturn(new CaptchaIssueResult("cid", "img", 60));

        MockHttpServletResponse httpResponse = new MockHttpServletResponse();
        Result<CaptchaIssueResponse> resp = controller.captcha(httpResponse);

        assertThat(httpResponse.getHeader(HttpHeaders.CACHE_CONTROL)).contains("no-store");
        assertThat(httpResponse.getHeader(HttpHeaders.PRAGMA)).contains("no-cache");
        assertThat(resp.getCode()).isEqualTo(0);
        assertThat(resp.getData()).isNotNull();
        assertThat(resp.getData().getCaptchaId()).isEqualTo("cid");
    }

    @Test
    void registerShouldReturnNewRegisterResponseContract() {
        UUID userId = UUID.fromString("00000000-0000-7000-8000-000000000007");
        RegisterRequest request = new RegisterRequest();
        request.setUsername("alice");
        request.setPassword("secret");
        request.setEmail("alice@example.com");
        request.setCaptchaId("cid");
        request.setCaptchaCode("abcd");

        when(registrationApplicationService.register(any(RegisterCommand.class))).thenReturn(new RegisterResult(
                userId,
                "0123456789abcdef0123456789abcdef",
                true,
                "a***@example.com",
                "123456"
        ));

        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        Result<RegisterResponse> response = controller.register(request, httpRequest);

        assertThat(response.getCode()).isEqualTo(0);
        assertThat(response.getData()).isNotNull();
        assertThat(response.getData().getRegistrationToken()).isEqualTo("0123456789abcdef0123456789abcdef");
        assertThat(response.getData().isEmailCodeIssued()).isTrue();
        assertThat(response.getData().getMaskedEmail()).isEqualTo("a***@example.com");
        assertThat(response.getData().getDebugEmailCode()).isEqualTo("123456");
    }

    @Test
    void resendRegisterCodeShouldReturnResponse() {
        RegisterCodeResendRequest request = new RegisterCodeResendRequest();
        request.setRegistrationToken("token");
        request.setCaptchaId("cid");
        request.setCaptchaCode("abcd");

        when(registrationVerificationApplicationService.resendCode(any(ResendRegisterCodeCommand.class)))
                .thenReturn(new RegisterCodeResendResult(true, "a***@example.com", "123456"));

        Result<RegisterCodeResendResponse> response = controller.resendRegisterCode(request);

        assertThat(response.getCode()).isEqualTo(0);
        assertThat(response.getData()).isNotNull();
        assertThat(response.getData().isIssued()).isTrue();
        assertThat(response.getData().getMaskedEmail()).isEqualTo("a***@example.com");
        assertThat(response.getData().getDebugEmailCode()).isEqualTo("123456");
    }

    @Test
    void verifyRegisterCodeShouldSetRefreshCookieAndReturnAccessToken() {
        RegisterCodeVerifyRequest request = new RegisterCodeVerifyRequest();
        request.setRegistrationToken("token");
        request.setCode("123456");

        RefreshCookieSpec refreshCookie = issuedCookie("rt3");

        when(registrationVerificationApplicationService.verifyAndLogin(any(VerifyRegisterCodeCommand.class)))
                .thenReturn(new LoginResult("at3", refreshCookie));

        MockHttpServletResponse httpResponse = new MockHttpServletResponse();
        Result<LoginResponse> response = controller.verifyRegisterCode(request, httpResponse);

        assertThat(response.getCode()).isEqualTo(0);
        assertThat(response.getData()).isNotNull();
        assertThat(response.getData().getAccessToken()).isEqualTo("at3");
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
}
