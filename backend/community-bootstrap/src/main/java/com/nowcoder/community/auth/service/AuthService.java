package com.nowcoder.community.auth.service;

import com.nowcoder.community.auth.api.AuthErrorCode;
import com.nowcoder.community.contracts.api.CommonErrorCode;
import com.nowcoder.community.contracts.exception.BusinessException;
import com.nowcoder.community.infra.web.net.ClientIpResolver;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import com.nowcoder.community.user.api.internal.dto.UserInternalAuthenticateResponse;
import com.nowcoder.community.user.api.internal.dto.UserInternalSessionProfileResponse;

import java.util.List;

@Service
public class AuthService {

    private final UserAuthAccess userAuthAccess;
    private final JwtTokenService jwtTokenService;
    private final RefreshTokenService refreshTokenService;
    private final LoginRateLimitService loginRateLimitService;
    private final CaptchaService captchaService;
    private final ClientIpResolver clientIpResolver;

    public AuthService(
            UserAuthAccess userAuthAccess,
            JwtTokenService jwtTokenService,
            RefreshTokenService refreshTokenService,
            LoginRateLimitService loginRateLimitService,
            CaptchaService captchaService,
            ClientIpResolver clientIpResolver
    ) {
        this.userAuthAccess = userAuthAccess;
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
                throw new BusinessException(AuthErrorCode.CAPTCHA_REQUIRED);
            }
            boolean ok = captchaService.verify(captchaId, captchaCode);
            if (!ok) {
                loginRateLimitService.recordFailure(username, ip, ipSource);
                throw new BusinessException(AuthErrorCode.CAPTCHA_INVALID);
            }
        }

        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            loginRateLimitService.recordFailure(username, ip, ipSource);
            throw new BusinessException(AuthErrorCode.INVALID_CREDENTIALS);
        }

        UserInternalAuthenticateResponse user;
        try {
            user = userAuthAccess.authenticate(username, password);
        } catch (BusinessException e) {
            int code = e.getErrorCode() == null ? 0 : e.getErrorCode().getCode();
            boolean invalidCredentials = code == AuthErrorCode.INVALID_CREDENTIALS.getCode()
                    || code == CommonErrorCode.UNAUTHORIZED.getCode();
            boolean userDisabled = code == AuthErrorCode.USER_DISABLED.getCode()
                    || code == CommonErrorCode.FORBIDDEN.getCode();
            if (invalidCredentials || userDisabled) {
                loginRateLimitService.recordFailure(username, ip, ipSource);
            }
            // user 模块使用通用码表达鉴权失败（避免跨域依赖 auth 域错误码），auth 模块在边界做语义翻译。
            if (code == CommonErrorCode.UNAUTHORIZED.getCode()) {
                throw new BusinessException(AuthErrorCode.INVALID_CREDENTIALS);
            }
            if (code == CommonErrorCode.FORBIDDEN.getCode()) {
                throw new BusinessException(AuthErrorCode.USER_DISABLED);
            }
            throw e;
        }

        loginRateLimitService.reset(username, ip);

        List<String> authorities = user.getAuthorities() == null ? List.of() : user.getAuthorities();
        String accessToken = jwtTokenService.createAccessToken(user.getUserId(), user.getUsername(), authorities);
        RefreshTokenService.IssuedRefreshToken refreshToken = refreshTokenService.issue(user.getUserId());
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

        UserInternalSessionProfileResponse profile = userAuthAccess.sessionProfile(stored.userId());
        if (profile == null || profile.getStatus() == 0) {
            throw new BusinessException(AuthErrorCode.USER_DISABLED);
        }

        RefreshTokenService.IssuedRefreshToken rotated = refreshTokenService.rotate(refreshToken);
        if (rotated == null) {
            throw new BusinessException(AuthErrorCode.REFRESH_TOKEN_INVALID);
        }
        List<String> authorities = profile.getAuthorities() == null ? List.of() : profile.getAuthorities();
        String accessToken = jwtTokenService.createAccessToken(profile.getUserId(), profile.getUsername(), authorities);
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
