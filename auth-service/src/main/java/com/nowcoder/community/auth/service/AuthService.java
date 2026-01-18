package com.nowcoder.community.auth.service;

import com.nowcoder.community.auth.config.JwtProperties;
import com.nowcoder.community.auth.user.User;
import com.nowcoder.community.auth.user.UserMapper;
import com.nowcoder.community.common.api.AuthErrorCode;
import com.nowcoder.community.common.api.CommonErrorCode;
import com.nowcoder.community.common.exception.BusinessException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
public class AuthService {

    private final UserMapper userMapper;
    private final JwtTokenService jwtTokenService;
    private final RefreshTokenService refreshTokenService;
    private final LoginRateLimitService loginRateLimitService;
    private final CaptchaService captchaService;
    private final JwtProperties jwtProperties;

    public AuthService(
            UserMapper userMapper,
            JwtTokenService jwtTokenService,
            RefreshTokenService refreshTokenService,
            LoginRateLimitService loginRateLimitService,
            CaptchaService captchaService,
            JwtProperties jwtProperties
    ) {
        this.userMapper = userMapper;
        this.jwtTokenService = jwtTokenService;
        this.refreshTokenService = refreshTokenService;
        this.loginRateLimitService = loginRateLimitService;
        this.captchaService = captchaService;
        this.jwtProperties = jwtProperties;
    }

    public LoginResult login(String username, String password, String captchaId, String captchaCode, HttpServletRequest request) {
        assertAllowedOrigin(request);

        String ip = clientIp(request);
        loginRateLimitService.assertNotBlocked(username, ip);

        if (loginRateLimitService.isCaptchaRequired(username, ip)) {
            if (!StringUtils.hasText(captchaId) || !StringUtils.hasText(captchaCode)) {
                loginRateLimitService.recordFailure(username, ip);
                throw new BusinessException(AuthErrorCode.CAPTCHA_REQUIRED);
            }
            boolean ok = captchaService.verify(captchaId, captchaCode);
            if (!ok) {
                loginRateLimitService.recordFailure(username, ip);
                throw new BusinessException(AuthErrorCode.CAPTCHA_INVALID);
            }
        }

        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            loginRateLimitService.recordFailure(username, ip);
            throw new BusinessException(AuthErrorCode.LOGIN_FAILED);
        }

        User user = userMapper.selectByName(username);
        if (user == null) {
            loginRateLimitService.recordFailure(username, ip);
            throw new BusinessException(AuthErrorCode.LOGIN_FAILED);
        }
        if (user.getStatus() == 0) {
            loginRateLimitService.recordFailure(username, ip);
            throw new BusinessException(AuthErrorCode.USER_DISABLED);
        }

        String encrypted = md5(password + user.getSalt());
        if (!encrypted.equals(user.getPassword())) {
            loginRateLimitService.recordFailure(username, ip);
            throw new BusinessException(AuthErrorCode.LOGIN_FAILED);
        }

        loginRateLimitService.reset(username, ip);

        List<String> authorities = authoritiesOf(user);
        String accessToken = jwtTokenService.createAccessToken(user.getId(), user.getUsername(), authorities);
        RefreshTokenService.IssuedRefreshToken refreshToken = refreshTokenService.issue(user.getId());
        return new LoginResult(accessToken, refreshToken.cookie());
    }

    public RefreshResult refresh(HttpServletRequest request) {
        assertAllowedOrigin(request);
        String refreshToken = readCookie(request, refreshTokenService.buildCookie("").getName());
        if (!StringUtils.hasText(refreshToken)) {
            throw new BusinessException(AuthErrorCode.REFRESH_TOKEN_INVALID);
        }

        RefreshTokenStore.StoredRefreshToken stored = refreshTokenService.find(refreshToken);
        if (stored == null) {
            throw new BusinessException(AuthErrorCode.REFRESH_TOKEN_INVALID);
        }

        User user = userMapper.selectById(stored.userId());
        if (user == null || user.getStatus() == 0) {
            throw new BusinessException(AuthErrorCode.USER_DISABLED);
        }

        RefreshTokenService.IssuedRefreshToken rotated = refreshTokenService.rotate(stored);
        String accessToken = jwtTokenService.createAccessToken(user.getId(), user.getUsername(), authoritiesOf(user));
        return new RefreshResult(accessToken, rotated.cookie());
    }

    public void logout(HttpServletRequest request) {
        assertAllowedOrigin(request);
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

    private String md5(String input) {
        return DigestUtils.md5DigestAsHex(input.getBytes(StandardCharsets.UTF_8));
    }

    private List<String> authoritiesOf(User user) {
        if (user.getType() == 1) {
            return List.of("ROLE_ADMIN");
        }
        if (user.getType() == 2) {
            return List.of("ROLE_MODERATOR");
        }
        return List.of("ROLE_USER");
    }

    private void assertAllowedOrigin(HttpServletRequest request) {
        if (request == null) {
            return;
        }
        String origin = request.getHeader("Origin");
        if (!StringUtils.hasText(origin)) {
            return;
        }
        if (jwtProperties.getAllowedOrigins() == null || jwtProperties.getAllowedOrigins().isEmpty()) {
            return;
        }
        if (!jwtProperties.getAllowedOrigins().contains(origin)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN, "Origin 不被允许");
        }
    }

    private String clientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            String first = forwarded.split(",")[0].trim();
            if (StringUtils.hasText(first)) {
                return first;
            }
        }
        return request.getRemoteAddr();
    }

    public record LoginResult(String accessToken, ResponseCookie refreshCookie) {
    }

    public record RefreshResult(String accessToken, ResponseCookie refreshCookie) {
    }
}
