package com.nowcoder.community.auth.api;

import com.nowcoder.community.auth.api.dto.LoginRequest;
import com.nowcoder.community.auth.api.dto.LoginResponse;
import com.nowcoder.community.auth.api.dto.MeResponse;
import com.nowcoder.community.auth.api.dto.CaptchaIssueResponse;
import com.nowcoder.community.auth.service.AuthService;
import com.nowcoder.community.auth.service.CaptchaService;
import com.nowcoder.community.auth.service.PasswordResetService;
import com.nowcoder.community.auth.service.RegistrationService;
import com.nowcoder.community.contracts.api.Result;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerUnitTest {

    @Mock
    private AuthService authService;

    @Mock
    private RegistrationService registrationService;

    @Mock
    private CaptchaService captchaService;

    @Mock
    private PasswordResetService passwordResetService;

    private AuthController controller;

    @BeforeEach
    void setUp() {
        controller = new AuthController(authService, registrationService, captchaService, passwordResetService);
    }

    @Test
    void loginShouldSetRefreshCookieAndReturnAccessToken() {
        LoginRequest req = new LoginRequest();
        req.setUsername("u");
        req.setPassword("p");

        ResponseCookie refreshCookie = ResponseCookie.from("refresh_token", "rt")
                .httpOnly(true)
                .path("/api/auth")
                .build();

        when(authService.login(eq("u"), eq("p"), isNull(), isNull(), any(HttpServletRequest.class)))
                .thenReturn(new AuthService.LoginResult("at", refreshCookie));

        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        MockHttpServletResponse httpResponse = new MockHttpServletResponse();

        Result<LoginResponse> resp = controller.login(req, httpRequest, httpResponse);
        assertThat(resp.getCode()).isEqualTo(0);
        assertThat(resp.getData()).isNotNull();
        assertThat(resp.getData().getAccessToken()).isEqualTo("at");

        String setCookie = httpResponse.getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).isNotBlank();
        assertThat(setCookie).contains("refresh_token=rt");
    }

    @Test
    void refreshShouldSetRefreshCookieAndReturnAccessToken() {
        ResponseCookie refreshCookie = ResponseCookie.from("refresh_token", "rt2")
                .httpOnly(true)
                .path("/api/auth")
                .build();

        when(authService.refresh(any(HttpServletRequest.class)))
                .thenReturn(new AuthService.RefreshResult("at2", refreshCookie));

        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        MockHttpServletResponse httpResponse = new MockHttpServletResponse();

        Result<LoginResponse> resp = controller.refresh(httpRequest, httpResponse);
        assertThat(resp.getCode()).isEqualTo(0);
        assertThat(resp.getData()).isNotNull();
        assertThat(resp.getData().getAccessToken()).isEqualTo("at2");

        String setCookie = httpResponse.getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).isNotBlank();
        assertThat(setCookie).contains("refresh_token=rt2");
    }

    @Test
    void logoutShouldClearRefreshCookie() {
        ResponseCookie clearCookie = ResponseCookie.from("refresh_token", "")
                .httpOnly(true)
                .path("/api/auth")
                .maxAge(0)
                .build();

        when(authService.clearRefreshCookie()).thenReturn(clearCookie);

        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        MockHttpServletResponse httpResponse = new MockHttpServletResponse();

        controller.logout(httpRequest, httpResponse, any(Authentication.class));

        verify(authService).logout(eq(httpRequest));
        assertThat(httpResponse.getHeader(HttpHeaders.SET_COOKIE)).contains("refresh_token=");
    }

    @Test
    void meShouldReadSubjectAndClaimsFromJwt() {
        Jwt jwt = Jwt.withTokenValue("t")
                .header("alg", "none")
                .subject("42")
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
        assertThat(resp.getData().getUserId()).isEqualTo(42);
        assertThat(resp.getData().getUsername()).isEqualTo("u42");
        assertThat(resp.getData().getAuthorities()).contains("ROLE_USER");
    }

    @Test
    void captchaShouldSetNoCacheHeaders() {
        when(captchaService.issue()).thenReturn(new CaptchaService.IssuedCaptcha("cid", "img", 60));

        MockHttpServletResponse httpResponse = new MockHttpServletResponse();
        Result<CaptchaIssueResponse> resp = controller.captcha(httpResponse);

        assertThat(httpResponse.getHeader(HttpHeaders.CACHE_CONTROL)).contains("no-store");
        assertThat(httpResponse.getHeader(HttpHeaders.PRAGMA)).contains("no-cache");
        assertThat(resp.getCode()).isEqualTo(0);
        assertThat(resp.getData()).isNotNull();
        assertThat(resp.getData().getCaptchaId()).isEqualTo("cid");
    }
}
