package com.nowcoder.community.auth.service;

import com.nowcoder.community.auth.dto.CaptchaIssueResponse;
import com.nowcoder.community.auth.dto.CaptchaVerifyRequest;
import com.nowcoder.community.auth.dto.LoginRequest;
import com.nowcoder.community.auth.dto.LoginResponse;
import com.nowcoder.community.auth.dto.PasswordResetConfirmRequest;
import com.nowcoder.community.auth.dto.PasswordResetRequestRequest;
import com.nowcoder.community.auth.dto.PasswordResetRequestResponse;
import com.nowcoder.community.auth.dto.RegisterCodeResendRequest;
import com.nowcoder.community.auth.dto.RegisterCodeResendResponse;
import com.nowcoder.community.auth.dto.RegisterCodeVerifyRequest;
import com.nowcoder.community.auth.dto.RegisterRequest;
import com.nowcoder.community.auth.dto.RegisterResponse;
import com.nowcoder.community.auth.exception.AuthErrorCode;
import com.nowcoder.community.common.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

@Service
public class AuthApplicationService {

    private final AuthService authService;
    private final RegistrationService registrationService;
    private final RegistrationVerificationService registrationVerificationService;
    private final CaptchaService captchaService;
    private final PasswordResetService passwordResetService;

    public AuthApplicationService(
            AuthService authService,
            RegistrationService registrationService,
            RegistrationVerificationService registrationVerificationService,
            CaptchaService captchaService,
            PasswordResetService passwordResetService
    ) {
        this.authService = authService;
        this.registrationService = registrationService;
        this.registrationVerificationService = registrationVerificationService;
        this.captchaService = captchaService;
        this.passwordResetService = passwordResetService;
    }

    public LoginResponse login(LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse response) {
        AuthService.LoginResult result = authService.login(
                request.getUsername(),
                request.getPassword(),
                request.getCaptchaId(),
                request.getCaptchaCode(),
                httpRequest
        );
        response.addHeader(HttpHeaders.SET_COOKIE, result.refreshCookie().toString());
        return new LoginResponse(result.accessToken());
    }

    public LoginResponse refresh(HttpServletRequest request, HttpServletResponse response) {
        try {
            AuthService.RefreshResult result = authService.refresh(request);
            response.addHeader(HttpHeaders.SET_COOKIE, result.refreshCookie().toString());
            return new LoginResponse(result.accessToken());
        } catch (BusinessException ex) {
            if (shouldClearRefreshCookie(ex)) {
                response.addHeader(HttpHeaders.SET_COOKIE, authService.clearRefreshCookie().toString());
            }
            throw ex;
        }
    }

    public void logout(HttpServletRequest request, HttpServletResponse response) {
        authService.logout(request);
        response.addHeader(HttpHeaders.SET_COOKIE, authService.clearRefreshCookie().toString());
    }

    public RegisterResponse register(RegisterRequest request, HttpServletRequest httpRequest) {
        return registrationService.register(request, httpRequest);
    }

    public RegisterCodeResendResponse resendRegisterCode(RegisterCodeResendRequest request) {
        return registrationVerificationService.resendCode(
                request.getRegistrationToken(),
                request.getCaptchaId(),
                request.getCaptchaCode()
        );
    }

    public LoginResponse verifyRegisterCode(RegisterCodeVerifyRequest request, HttpServletResponse response) {
        AuthService.LoginResult result = registrationVerificationService.verifyAndLogin(request.getRegistrationToken(), request.getCode());
        response.addHeader(HttpHeaders.SET_COOKIE, result.refreshCookie().toString());
        return new LoginResponse(result.accessToken());
    }

    public CaptchaIssueResponse captcha(HttpServletResponse response) {
        CaptchaService.IssuedCaptcha issued = captchaService.issue();
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate, max-age=0");
        response.setHeader(HttpHeaders.PRAGMA, "no-cache");
        return new CaptchaIssueResponse(issued.captchaId(), issued.imageBase64(), issued.ttlSeconds());
    }

    public boolean verifyCaptcha(CaptchaVerifyRequest request) {
        return captchaService.verify(request.getCaptchaId(), request.getCode());
    }

    public PasswordResetRequestResponse requestPasswordReset(PasswordResetRequestRequest request) {
        PasswordResetService.RequestResult result = passwordResetService.requestReset(
                request.getEmail(),
                request.getCaptchaId(),
                request.getCaptchaCode()
        );
        return new PasswordResetRequestResponse(result.issued(), result.resetLink());
    }

    public boolean confirmPasswordReset(PasswordResetConfirmRequest request) {
        return passwordResetService.confirmReset(
                request.getResetToken(),
                request.getNewPassword(),
                request.getCaptchaId(),
                request.getCaptchaCode()
        );
    }

    private boolean shouldClearRefreshCookie(BusinessException ex) {
        if (ex == null || ex.getErrorCode() == null) {
            return false;
        }
        int code = ex.getErrorCode().getCode();
        return code == AuthErrorCode.USER_DISABLED.getCode()
                || code == AuthErrorCode.REFRESH_TOKEN_INVALID.getCode();
    }
}
