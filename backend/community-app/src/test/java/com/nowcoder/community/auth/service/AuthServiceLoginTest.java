package com.nowcoder.community.auth.service;

import com.nowcoder.community.auth.exception.AuthErrorCode;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.infra.web.net.ClientIpResolver;
import com.nowcoder.community.user.entity.User;
import com.nowcoder.community.user.mapper.UserMapper;
import com.nowcoder.community.user.service.InternalUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceLoginTest {

    private final UserMapper userMapper = mock(UserMapper.class);
    private final JwtTokenService jwtTokenService = mock(JwtTokenService.class);
    private final RefreshTokenService refreshTokenService = mock(RefreshTokenService.class);
    private final LoginRateLimitService loginRateLimitService = mock(LoginRateLimitService.class);
    private final CaptchaService captchaService = mock(CaptchaService.class);
    private final ClientIpResolver clientIpResolver = mock(ClientIpResolver.class);

    private AuthService authService;

    @BeforeEach
    void setUp() {
        InternalUserService internalUserService = new InternalUserService(userMapper);
        authService = new AuthService(
                internalUserService,
                jwtTokenService,
                refreshTokenService,
                loginRateLimitService,
                captchaService,
                clientIpResolver
        );
        when(clientIpResolver.resolve(any())).thenReturn(new ClientIpResolver.ResolvedClientIp("127.0.0.1", ClientIpResolver.SOURCE_REMOTE));
    }

    @Test
    void loginShouldRecordFailureWhenCredentialsAreInvalid() {
        when(userMapper.selectByName("alice")).thenReturn(null);

        Throwable thrown = catchThrowable(() -> authService.login("alice", "wrong-password", null, null, new MockHttpServletRequest()));

        assertThat(thrown).isInstanceOf(BusinessException.class);
        BusinessException error = (BusinessException) thrown;
        assertThat(error.getErrorCode()).isEqualTo(AuthErrorCode.INVALID_CREDENTIALS);
        verify(loginRateLimitService).recordFailure("alice", "127.0.0.1", ClientIpResolver.SOURCE_REMOTE);
        verify(loginRateLimitService, never()).reset(any(), any());
    }

    @Test
    void loginShouldRecordFailureWhenUserIsDisabled() {
        User disabledUser = new User();
        disabledUser.setId(7);
        disabledUser.setUsername("alice");
        disabledUser.setStatus(0);
        when(userMapper.selectByName("alice")).thenReturn(disabledUser);

        Throwable thrown = catchThrowable(() -> authService.login("alice", "secret", null, null, new MockHttpServletRequest()));

        assertThat(thrown).isInstanceOf(BusinessException.class);
        BusinessException error = (BusinessException) thrown;
        assertThat(error.getErrorCode()).isEqualTo(AuthErrorCode.USER_DISABLED);
        verify(loginRateLimitService).recordFailure("alice", "127.0.0.1", ClientIpResolver.SOURCE_REMOTE);
        verify(loginRateLimitService, never()).reset(any(), any());
    }

    @Test
    void loginShouldResetRateLimitAfterSuccessfulAuthentication() {
        BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
        User user = new User();
        user.setId(7);
        user.setUsername("alice");
        user.setStatus(1);
        user.setType(0);
        user.setPassword(passwordEncoder.encode("secret"));
        when(userMapper.selectByName("alice")).thenReturn(user);

        ResponseCookie cookie = ResponseCookie.from("refresh_token", "rt").path("/api/auth").httpOnly(true).build();
        when(jwtTokenService.createAccessToken(eq(7), eq("alice"), eq(java.util.List.of("ROLE_USER")))).thenReturn("access-token");
        when(refreshTokenService.issue(7)).thenReturn(new RefreshTokenService.IssuedRefreshToken("rt", cookie));

        AuthService.LoginResult result = authService.login("alice", "secret", null, null, new MockHttpServletRequest());

        assertThat(result.accessToken()).isEqualTo("access-token");
        assertThat(result.refreshCookie()).isEqualTo(cookie);
        verify(loginRateLimitService).reset("alice", "127.0.0.1");
        verify(loginRateLimitService, never()).recordFailure(any(), any(), any());
    }
}
