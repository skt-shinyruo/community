package com.nowcoder.community.auth.application;

import com.nowcoder.community.auth.application.port.AuthTokenPort;
import com.nowcoder.community.auth.application.result.LoginResult;
import com.nowcoder.community.user.api.model.UserCredentialView;
import com.nowcoder.community.user.api.query.UserCredentialQueryApi;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class LoginTokenIssuer {

    private final UserCredentialQueryApi userCredentialQueryApi;
    private final AuthTokenPort authTokenPort;
    private final RefreshTokenApplicationService refreshTokenService;

    public LoginTokenIssuer(
            UserCredentialQueryApi userCredentialQueryApi,
            AuthTokenPort authTokenPort,
            RefreshTokenApplicationService refreshTokenService
    ) {
        this.userCredentialQueryApi = userCredentialQueryApi;
        this.authTokenPort = authTokenPort;
        this.refreshTokenService = refreshTokenService;
    }

    public LoginResult issueLoginResult(UserCredentialView user) {
        String accessToken = issueAccessToken(user);
        RefreshTokenApplicationService.IssuedRefreshToken refreshToken = refreshTokenService.issue(
                user.userId(),
                user.securityVersion()
        );
        return new LoginResult(accessToken, refreshToken.cookie());
    }

    public String issueAccessToken(UserCredentialView user) {
        List<String> authorities = userCredentialQueryApi.authoritiesOf(user);
        return authTokenPort.createAccessToken(
                user.userId(),
                user.username(),
                authorities,
                user.securityVersion()
        );
    }
}
