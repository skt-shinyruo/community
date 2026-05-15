package com.nowcoder.community.auth.application;

import com.nowcoder.community.analytics.api.action.AnalyticsIngestActionApi;
import com.nowcoder.community.auth.application.command.LoginCommand;
import com.nowcoder.community.auth.application.command.LogoutCommand;
import com.nowcoder.community.auth.application.command.RefreshCommand;
import com.nowcoder.community.auth.application.result.LoginResult;
import com.nowcoder.community.auth.application.result.RefreshCookieSpec;
import com.nowcoder.community.auth.application.result.RefreshResult;
import com.nowcoder.community.auth.application.port.AuthTokenPort;
import com.nowcoder.community.auth.domain.repository.RefreshTokenRepository;
import com.nowcoder.community.auth.domain.service.AuthDomainService;
import com.nowcoder.community.auth.exception.AuthErrorCode;
import com.nowcoder.community.auth.logging.SecurityEventLogger;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.user.api.model.UserAuthenticationResultView;
import com.nowcoder.community.user.api.model.UserCredentialView;
import com.nowcoder.community.user.api.query.UserCredentialQueryApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.UUID;

@Service
public class LoginApplicationService {

    private static final Logger log = LoggerFactory.getLogger(LoginApplicationService.class);

    private final UserCredentialQueryApi userCredentialQueryApi;
    private final AuthTokenPort authTokenPort;
    private final RefreshTokenApplicationService refreshTokenService;
    private final LoginRateLimitApplicationService loginRateLimitService;
    private final CaptchaApplicationService captchaService;
    private final AuthDomainService authDomainService;
    private final AnalyticsIngestActionApi analyticsIngestService;

    public LoginApplicationService(
            UserCredentialQueryApi userCredentialQueryApi,
            AuthTokenPort authTokenPort,
            RefreshTokenApplicationService refreshTokenService,
            LoginRateLimitApplicationService loginRateLimitService,
            CaptchaApplicationService captchaService,
            AuthDomainService authDomainService,
            AnalyticsIngestActionApi analyticsIngestService
    ) {
        this.userCredentialQueryApi = userCredentialQueryApi;
        this.authTokenPort = authTokenPort;
        this.refreshTokenService = refreshTokenService;
        this.loginRateLimitService = loginRateLimitService;
        this.captchaService = captchaService;
        this.authDomainService = authDomainService;
        this.analyticsIngestService = analyticsIngestService;
    }

    public LoginResult login(LoginCommand command) {
        String username = command == null ? null : command.username();
        String password = command == null ? null : command.password();
        String captchaId = command == null ? null : command.captchaId();
        String captchaCode = command == null ? null : command.captchaCode();
        String ip = command == null ? null : command.clientIp();
        String ipSource = command == null ? null : command.clientIpSource();

        loginRateLimitService.assertNotBlocked(username, ip, ipSource);

        if (loginRateLimitService.isCaptchaRequired(username, ip)) {
            if (!StringUtils.hasText(captchaId) || !StringUtils.hasText(captchaCode)) {
                loginRateLimitService.recordFailure(username, ip, ipSource);
                SecurityEventLogger.info(log, "login", "denied",
                        "community.reason_code", "captcha_required",
                        "username", username,
                        "source.ip", ip,
                        "ip.source", ipSource);
                throw new BusinessException(AuthErrorCode.CAPTCHA_REQUIRED);
            }
            boolean ok = captchaService.verify(captchaId, captchaCode);
            if (!ok) {
                loginRateLimitService.recordFailure(username, ip, ipSource);
                SecurityEventLogger.info(log, "login", "denied",
                        "community.reason_code", "captcha_invalid",
                        "username", username,
                        "source.ip", ip,
                        "ip.source", ipSource);
                throw new BusinessException(AuthErrorCode.CAPTCHA_INVALID);
            }
        }

        try {
            authDomainService.requireCredentials(username, password);
        } catch (BusinessException e) {
            loginRateLimitService.recordFailure(username, ip, ipSource);
            SecurityEventLogger.info(log, "login", "denied",
                    "community.reason_code", "invalid_credentials",
                    "username", username,
                    "source.ip", ip,
                    "ip.source", ipSource);
            throw e;
        }

        UserCredentialView user;
        try {
            UserAuthenticationResultView authenticationResult = authenticateUser(username, password);
            if (!authenticationResult.authenticated()) {
                throw authenticationFailure(authenticationResult);
            }
            user = authenticationResult.user();
        } catch (BusinessException e) {
            int code = e.getErrorCode() == null ? 0 : e.getErrorCode().getCode();
            boolean invalidCredentials = code == AuthErrorCode.INVALID_CREDENTIALS.getCode();
            boolean userDisabled = code == AuthErrorCode.USER_DISABLED.getCode();
            if (invalidCredentials || userDisabled) {
                loginRateLimitService.recordFailure(username, ip, ipSource);
                String reason = invalidCredentials ? "invalid_credentials" : "user_disabled";
                SecurityEventLogger.info(log, "login", "denied",
                        "community.reason_code", reason,
                        "username", username,
                        "source.ip", ip,
                        "ip.source", ipSource);
            }
            throw e;
        }

        loginRateLimitService.reset(username, ip);
        LoginResult loginResult = issueLoginResult(user);
        SecurityEventLogger.info(log, "login", "success",
                "user.id", user.userId(),
                "username", user.username(),
                "source.ip", ip,
                "ip.source", ipSource);
        analyticsIngestService.recordLoginSuccess(user.userId());
        return loginResult;
    }

    public LoginResult issueLoginResult(UserCredentialView user) {
        List<String> authorities = authoritiesOf(user);
        String accessToken = authTokenPort.createAccessToken(user.userId(), user.username(), authorities);
        RefreshTokenApplicationService.IssuedRefreshToken refreshToken = refreshTokenService.issue(user.userId());
        return new LoginResult(accessToken, refreshToken.cookie());
    }

    public RefreshResult refresh(RefreshCommand command) {
        String refreshToken = command == null ? null : command.refreshToken();
        if (!StringUtils.hasText(refreshToken)) {
            throw new BusinessException(AuthErrorCode.REFRESH_TOKEN_INVALID);
        }

        RefreshTokenRepository.StoredRefreshToken consumed = refreshTokenService.consume(refreshToken);
        if (consumed == null) {
            throw new BusinessException(AuthErrorCode.REFRESH_TOKEN_INVALID);
        }

        UserCredentialView credentialView;
        try {
            credentialView = getCredential(consumed.userId());
        } catch (BusinessException ex) {
            refreshTokenService.revokeFamily(consumed.familyId());
            throw new BusinessException(AuthErrorCode.USER_DISABLED);
        }
        if (credentialView == null || credentialView.status() == 0) {
            refreshTokenService.revokeFamily(consumed.familyId());
            throw new BusinessException(AuthErrorCode.USER_DISABLED);
        }
        List<String> authorities = authoritiesOf(credentialView);
        String accessToken = authTokenPort.createAccessToken(
                credentialView.userId(),
                credentialView.username(),
                authorities
        );
        RefreshTokenApplicationService.IssuedRefreshToken rotated;
        try {
            rotated = refreshTokenService.issueInFamily(credentialView.userId(), consumed.familyId());
        } catch (RuntimeException ex) {
            throw new BusinessException(AuthErrorCode.REFRESH_TOKEN_INVALID);
        }
        if (rotated == null) {
            throw new BusinessException(AuthErrorCode.REFRESH_TOKEN_INVALID);
        }
        return new RefreshResult(accessToken, rotated.cookie());
    }

    public void logout(LogoutCommand command) {
        String refreshToken = command == null ? null : command.refreshToken();
        if (StringUtils.hasText(refreshToken)) {
            refreshTokenService.revokeFamilyByToken(refreshToken);
        }
    }

    public RefreshCookieSpec clearRefreshCookie() {
        return refreshTokenService.clearCookie();
    }

    public String refreshCookieName() {
        return refreshTokenService.refreshCookieName();
    }

    private BusinessException authenticationFailure(UserAuthenticationResultView authenticationResult) {
        if (authenticationResult != null && authenticationResult.failure() == UserAuthenticationResultView.Failure.USER_DISABLED) {
            return new BusinessException(AuthErrorCode.USER_DISABLED);
        }
        return new BusinessException(AuthErrorCode.INVALID_CREDENTIALS);
    }

    private UserAuthenticationResultView authenticateUser(String username, String password) {
        return userCredentialQueryApi.authenticate(username, password);
    }

    private UserCredentialView getCredential(UUID userId) {
        return userCredentialQueryApi.getByUserId(userId);
    }

    private List<String> authoritiesOf(UserCredentialView user) {
        return userCredentialQueryApi.authoritiesOf(user);
    }

}
