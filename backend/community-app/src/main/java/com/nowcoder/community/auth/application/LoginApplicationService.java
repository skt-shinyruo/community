package com.nowcoder.community.auth.application;

import com.nowcoder.community.analytics.api.action.AnalyticsIngestActionApi;
import com.nowcoder.community.auth.application.command.LoginCommand;
import com.nowcoder.community.auth.application.command.LogoutCommand;
import com.nowcoder.community.auth.application.command.RefreshCommand;
import com.nowcoder.community.auth.application.result.LoginResult;
import com.nowcoder.community.auth.application.result.RefreshFailure;
import com.nowcoder.community.auth.application.result.RefreshCookieSpec;
import com.nowcoder.community.auth.application.result.RefreshResult;
import com.nowcoder.community.auth.domain.repository.RefreshTokenRepository;
import com.nowcoder.community.auth.domain.service.AuthDomainService;
import com.nowcoder.community.auth.exception.AuthErrorCode;
import com.nowcoder.community.auth.logging.SecurityEventLogger;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.exception.CommonErrorCode;
import com.nowcoder.community.user.api.model.UserAuthenticationResultView;
import com.nowcoder.community.user.api.model.UserCredentialView;
import com.nowcoder.community.user.api.query.UserCredentialQueryApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Objects;
import java.util.UUID;

@Service
public class LoginApplicationService {

    private static final Logger log = LoggerFactory.getLogger(LoginApplicationService.class);

    private final UserCredentialQueryApi userCredentialQueryApi;
    private final LoginTokenIssuer loginTokenIssuer;
    private final RefreshTokenApplicationService refreshTokenService;
    private final LoginRateLimitApplicationService loginRateLimitService;
    private final CaptchaChallengeComponent captchaChallenge;
    private final AuthDomainService authDomainService;
    private final AnalyticsIngestActionApi analyticsIngestService;

    public LoginApplicationService(
            UserCredentialQueryApi userCredentialQueryApi,
            LoginTokenIssuer loginTokenIssuer,
            RefreshTokenApplicationService refreshTokenService,
            LoginRateLimitApplicationService loginRateLimitService,
            CaptchaChallengeComponent captchaChallenge,
            AuthDomainService authDomainService,
            AnalyticsIngestActionApi analyticsIngestService
    ) {
        this.userCredentialQueryApi = userCredentialQueryApi;
        this.loginTokenIssuer = loginTokenIssuer;
        this.refreshTokenService = refreshTokenService;
        this.loginRateLimitService = loginRateLimitService;
        this.captchaChallenge = captchaChallenge;
        this.authDomainService = authDomainService;
        this.analyticsIngestService = analyticsIngestService;
    }

    public LoginResult login(LoginCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        String suppliedUsername = command.username();
        String password = command.password();
        String captchaId = command.captchaId();
        String captchaCode = command.captchaCode();
        String ip = command.clientIp();
        String ipSource = command.clientIpSource();

        String username;
        try {
            username = authDomainService.requireCredentials(suppliedUsername, password);
        } catch (BusinessException e) {
            loginRateLimitService.recordFailure(null, ip, ipSource);
            SecurityEventLogger.info(log, "login", "denied",
                    "community.reason_code", "invalid_credentials",
                    "username", suppliedUsername,
                    "source.ip", ip,
                    "ip.source", ipSource);
            throw e;
        }

        LoginRateLimitApplicationService.PasswordCheckPermit permit =
                loginRateLimitService.acquirePasswordCheck(username, ip, ipSource);
        UserCredentialView user;
        String riskSubject;
        UUID challengeUserId;
        try {
            UserCredentialQueryApi.AuthenticationSubject authenticationSubject =
                    userCredentialQueryApi.authenticationSubject(username);
            if (authenticationSubject == null || !StringUtils.hasText(authenticationSubject.value())) {
                throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE,
                        "用户认证服务暂时不可用");
            }
            riskSubject = authenticationSubject.value();
            loginRateLimitService.attachAuthenticationSubject(
                    permit, username, riskSubject, ipSource);

            UserCredentialQueryApi.AuthenticationChallenge authenticationChallenge =
                    userCredentialQueryApi.prepareAuthentication(username);
            if (authenticationChallenge == null) {
                throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE,
                        "用户认证服务暂时不可用");
            }
            challengeUserId = authenticationChallenge.userId();
            boolean captchaRequired = loginRateLimitService.isCaptchaRequired(
                    riskSubject, ip, permit);
            if (captchaRequired) {
                if (!StringUtils.hasText(captchaId) || !StringUtils.hasText(captchaCode)) {
                    loginRateLimitService.recordFailure(riskSubject, ip, ipSource);
                    SecurityEventLogger.info(log, "login", "denied",
                            "community.reason_code", "captcha_required",
                            "username", username,
                            "source.ip", ip,
                            "ip.source", ipSource);
                    throw new BusinessException(AuthErrorCode.CAPTCHA_REQUIRED);
                }
                boolean ok = captchaChallenge.verify(captchaId, captchaCode);
                if (!ok) {
                    loginRateLimitService.recordFailure(riskSubject, ip, ipSource);
                    SecurityEventLogger.info(log, "login", "denied",
                            "community.reason_code", "captcha_invalid",
                            "username", username,
                            "source.ip", ip,
                            "ip.source", ipSource);
                    throw new BusinessException(AuthErrorCode.CAPTCHA_INVALID);
                }
            }
            loginRateLimitService.assertPasswordCheckOwned(permit);
            user = authenticateWithReservedBudget(
                    authenticationChallenge,
                    username,
                    riskSubject,
                    challengeUserId,
                    password,
                    ip,
                    ipSource,
                    permit
            );
        } finally {
            loginRateLimitService.releasePasswordCheck(permit);
        }

        loginRateLimitService.resetSubject(riskSubject);
        LoginResult loginResult = loginTokenIssuer.issueLoginResult(user);
        SecurityEventLogger.info(log, "login", "success",
                "user.id", user.userId(),
                "username", user.username(),
                "source.ip", ip,
                "ip.source", ipSource);
        analyticsIngestService.recordLoginSuccess(user.userId());
        return loginResult;
    }

    public RefreshResult refresh(RefreshCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        String refreshToken = command.refreshToken();
        if (!StringUtils.hasText(refreshToken)) {
            throw new RefreshFailure(AuthErrorCode.REFRESH_TOKEN_INVALID);
        }

        RefreshTokenRepository.StoredRefreshToken pending = refreshTokenService.beginRotation(refreshToken);
        if (pending == null) {
            throw new RefreshFailure(AuthErrorCode.REFRESH_TOKEN_INVALID);
        }

        UserCredentialView credentialView;
        try {
            credentialView = getCredential(pending.userId());
            if (credentialView == null || !credentialView.loginAllowed() || !credentialView.refreshAllowed()) {
                refreshTokenService.revokeFamily(pending.familyId());
                throw new RefreshFailure(AuthErrorCode.USER_DISABLED);
            }
            if (pending.securityVersionAtIssue() != credentialView.securityVersion()) {
                refreshTokenService.revokeFamily(pending.familyId());
                throw new RefreshFailure(AuthErrorCode.REFRESH_TOKEN_INVALID);
            }
            String accessToken = loginTokenIssuer.issueAccessToken(credentialView);
            RefreshTokenApplicationService.IssuedRefreshToken replacement = refreshTokenService.generateReplacementToken(
                    credentialView.userId(),
                    pending.familyId()
            );
            if (replacement == null || !StringUtils.hasText(replacement.refreshToken())) {
                throw new IllegalStateException("refresh replacement token generation failed");
            }
            boolean finished = refreshTokenService.finishRotation(
                    refreshToken,
                    replacement.refreshToken(),
                    credentialView.userId(),
                    pending.familyId(),
                    credentialView.securityVersion(),
                    pending.rotationLeaseId()
            );
            if (!finished) {
                throw new IllegalStateException("refresh rotation finish failed");
            }
            return new RefreshResult(accessToken, replacement.cookie());
        } catch (RefreshFailure ex) {
            throw ex;
        } catch (BusinessException ex) {
            throw recoverRefreshFailure(refreshToken, pending, ex);
        } catch (RuntimeException ex) {
            throw recoverRefreshFailure(refreshToken, pending, ex);
        }
    }

    public void logout(LogoutCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        String refreshToken = command.refreshToken();
        if (StringUtils.hasText(refreshToken)) {
            refreshTokenService.revokeFamilyByToken(refreshToken);
        }
    }

    private RefreshFailure recoverRefreshFailure(
            String refreshToken,
            RefreshTokenRepository.StoredRefreshToken pending,
            RuntimeException cause
    ) {
        boolean rolledBack = refreshTokenService.rollbackPendingRotation(
                refreshToken,
                pending.rotationLeaseId()
        );
        if (rolledBack) {
            return new RefreshFailure(CommonErrorCode.SERVICE_UNAVAILABLE, CommonErrorCode.SERVICE_UNAVAILABLE.getMessage(), cause);
        }
        refreshTokenService.revokeFamily(pending.familyId());
        return new RefreshFailure(CommonErrorCode.SERVICE_UNAVAILABLE, CommonErrorCode.SERVICE_UNAVAILABLE.getMessage(), cause);
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

    private UserCredentialView authenticateWithReservedBudget(
            UserCredentialQueryApi.AuthenticationChallenge authenticationChallenge,
            String username,
            String riskSubject,
            UUID challengeUserId,
            String password,
            String ip,
            String ipSource,
            LoginRateLimitApplicationService.PasswordCheckPermit permit
    ) {
        try {
            UserAuthenticationResultView authenticationResult = authenticationChallenge.authenticate(password);
            loginRateLimitService.assertPasswordCheckOwned(permit);
            if (!authenticationResult.authenticated()) {
                throw authenticationFailure(authenticationResult);
            }
            if (challengeUserId != null && !challengeUserId.equals(authenticationResult.user().userId())) {
                throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE,
                        "用户认证身份已变化，请稍后重试");
            }
            return authenticationResult.user();
        } catch (BusinessException e) {
            int code = e.getErrorCode() == null ? 0 : e.getErrorCode().getCode();
            boolean invalidCredentials = code == AuthErrorCode.INVALID_CREDENTIALS.getCode();
            boolean userDisabled = code == AuthErrorCode.USER_DISABLED.getCode();
            if (invalidCredentials || userDisabled) {
                // Commit the failure before releasing the in-flight slot so
                // another burst cannot enter between the two controls.
                loginRateLimitService.recordFailure(riskSubject, ip, ipSource);
                String reason = invalidCredentials ? "invalid_credentials" : "user_disabled";
                SecurityEventLogger.info(log, "login", "denied",
                        "community.reason_code", reason,
                        "username", username,
                        "source.ip", ip,
                        "ip.source", ipSource);
            }
            throw e;
        }
    }

    private UserCredentialView getCredential(UUID userId) {
        return userCredentialQueryApi.getByUserId(userId);
    }

}
