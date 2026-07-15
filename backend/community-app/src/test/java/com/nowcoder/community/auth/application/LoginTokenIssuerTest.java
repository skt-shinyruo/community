package com.nowcoder.community.auth.application;

import com.nowcoder.community.auth.application.port.AuthTokenPort;
import com.nowcoder.community.auth.application.result.LoginResult;
import com.nowcoder.community.auth.application.result.RefreshCookieSpec;
import com.nowcoder.community.user.api.model.UserCredentialView;
import com.nowcoder.community.user.api.query.UserCredentialQueryApi;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LoginTokenIssuerTest {

    @Test
    void issueLoginResultShouldPersistCurrentSecurityVersionInRefreshToken() {
        UUID userId = UUID.fromString("00000000-0000-7000-8000-000000000007");
        long securityVersion = 42L;
        UserCredentialView user = new UserCredentialView(
                userId,
                "alice",
                1,
                0,
                "h1",
                securityVersion,
                true,
                true
        );
        UserCredentialQueryApi userCredentialQueryApi = mock(UserCredentialQueryApi.class);
        AuthTokenPort authTokenPort = mock(AuthTokenPort.class);
        RefreshTokenApplicationService refreshTokenService = mock(RefreshTokenApplicationService.class);
        RefreshCookieSpec cookie = new RefreshCookieSpec(
                "refresh_token",
                "refresh-token",
                true,
                false,
                "/api/auth",
                "Lax",
                600
        );
        when(userCredentialQueryApi.authoritiesOf(user)).thenReturn(List.of("ROLE_USER"));
        when(authTokenPort.createAccessToken(userId, "alice", List.of("ROLE_USER"), securityVersion))
                .thenReturn("access-token");
        when(refreshTokenService.issue(userId, securityVersion))
                .thenReturn(new RefreshTokenApplicationService.IssuedRefreshToken("refresh-token", cookie));
        LoginTokenIssuer issuer = new LoginTokenIssuer(userCredentialQueryApi, authTokenPort, refreshTokenService);

        LoginResult result = issuer.issueLoginResult(user);

        assertThat(result.accessToken()).isEqualTo("access-token");
        assertThat(result.refreshCookie()).isEqualTo(cookie);
        verify(refreshTokenService).issue(userId, securityVersion);
    }
}
