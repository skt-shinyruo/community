package com.nowcoder.community.auth.service;

import com.nowcoder.community.auth.exception.AuthErrorCode;
import com.nowcoder.community.auth.logging.SecurityEventLogger;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.infra.web.net.ClientIpResolver;
import com.nowcoder.community.user.entity.User;
import com.nowcoder.community.user.service.InternalUserService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final InternalUserService internalUserService;
    private final JwtTokenService jwtTokenService;
    private final RefreshTokenService refreshTokenService;
    private final LoginRateLimitService loginRateLimitService;
    private final CaptchaService captchaService;
    private final ClientIpResolver clientIpResolver;

    public AuthService(
            InternalUserService internalUserService,
            JwtTokenService jwtTokenService,
            RefreshTokenService refreshTokenService,
            LoginRateLimitService loginRateLimitService,
            CaptchaService captchaService,
            ClientIpResolver clientIpResolver
    ) {
        this.internalUserService = internalUserService;
        this.jwtTokenService = jwtTokenService;
        this.refreshTokenService = refreshTokenService;
        this.loginRateLimitService = loginRateLimitService;
        this.captchaService = captchaService;
        this.clientIpResolver = clientIpResolver;
    }

    public LoginResult login(String username, String password, String captchaId, String captchaCode, HttpServletRequest request) {
        ClientIpResolver.ResolvedClientIp resolved = clientIpResolver.resolve(request);
        String ip = resolved == null ? null : resolved.ip();
        String ipSource = resolved == null ? null : resolved.source();

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

        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            loginRateLimitService.recordFailure(username, ip, ipSource);
            SecurityEventLogger.info(log, "login", "denied",
                    "community.reason_code", "invalid_credentials",
                    "username", username,
                    "source.ip", ip,
                    "ip.source", ipSource);
            throw new BusinessException(AuthErrorCode.INVALID_CREDENTIALS);
        }

        User user;
        try {
            user = internalUserService.authenticate(username, password);
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
                "user.id", user.getId(),
                "username", user.getUsername(),
                "source.ip", ip,
                "ip.source", ipSource);
        return loginResult;
    }

    public LoginResult issueLoginResult(User user) {
        List<String> authorities = internalUserService.authoritiesOf(user);
        String accessToken = jwtTokenService.createAccessToken(user.getId(), user.getUsername(), authorities);
        RefreshTokenService.IssuedRefreshToken refreshToken = refreshTokenService.issue(user.getId());
        return new LoginResult(accessToken, refreshToken.cookie());
    }

    public RefreshResult refresh(HttpServletRequest request) {
        String refreshToken = readCookie(request, refreshTokenService.buildCookie("").getName());
        if (!StringUtils.hasText(refreshToken)) {
            throw new BusinessException(AuthErrorCode.REFRESH_TOKEN_INVALID);
        }

        RefreshTokenStore.StoredRefreshToken stored = refreshTokenService.find(refreshToken);
        if (stored == null) {
            throw new BusinessException(AuthErrorCode.REFRESH_TOKEN_INVALID);
        }

        User profile = internalUserService.getSessionProfile(stored.userId());
        if (profile == null || profile.getStatus() == 0) {
            throw new BusinessException(AuthErrorCode.USER_DISABLED);
        }

        RefreshTokenService.IssuedRefreshToken rotated = refreshTokenService.rotate(refreshToken);
        if (rotated == null) {
            throw new BusinessException(AuthErrorCode.REFRESH_TOKEN_INVALID);
        }
        List<String> authorities = internalUserService.authoritiesOf(profile);
        String accessToken = jwtTokenService.createAccessToken(profile.getId(), profile.getUsername(), authorities);
        return new RefreshResult(accessToken, rotated.cookie());
    }

    public void logout(HttpServletRequest request) {
        String refreshToken = readCookie(request, refreshTokenService.buildCookie("").getName());
        if (StringUtils.hasText(refreshToken)) {
            refreshTokenService.revokeFamilyByToken(refreshToken);
        }
    }

    public ResponseCookie clearRefreshCookie() {
        return refreshTokenService.clearCookie();
    }

    private String readCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    public record LoginResult(String accessToken, ResponseCookie refreshCookie) {
    }

    public record RefreshResult(String accessToken, ResponseCookie refreshCookie) {
    }
}
