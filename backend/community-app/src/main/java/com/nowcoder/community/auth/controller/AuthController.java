package com.nowcoder.community.auth.controller;

import com.nowcoder.community.auth.dto.LoginRequest;
import com.nowcoder.community.auth.dto.LoginResponse;
import com.nowcoder.community.auth.dto.MeResponse;
import com.nowcoder.community.auth.dto.CaptchaVerifyRequest;
import com.nowcoder.community.auth.dto.CaptchaIssueResponse;
import com.nowcoder.community.auth.dto.RegisterCodeResendRequest;
import com.nowcoder.community.auth.dto.RegisterCodeResendResponse;
import com.nowcoder.community.auth.dto.RegisterCodeVerifyRequest;
import com.nowcoder.community.auth.dto.RegisterRequest;
import com.nowcoder.community.auth.dto.RegisterResponse;
import com.nowcoder.community.auth.dto.PasswordResetConfirmRequest;
import com.nowcoder.community.auth.dto.PasswordResetRequestRequest;
import com.nowcoder.community.auth.dto.PasswordResetRequestResponse;
import com.nowcoder.community.auth.service.AuthService;
import com.nowcoder.community.auth.service.CaptchaService;
import com.nowcoder.community.auth.service.PasswordResetService;
import com.nowcoder.community.auth.service.RegistrationService;
import com.nowcoder.community.auth.service.RegistrationVerificationService;
import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.infra.security.auth.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final RegistrationService registrationService;
    private final RegistrationVerificationService registrationVerificationService;
    private final CaptchaService captchaService;
    private final PasswordResetService passwordResetService;

    public AuthController(
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

    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse response) {
        AuthService.LoginResult result = authService.login(
                request.getUsername(),
                request.getPassword(),
                request.getCaptchaId(),
                request.getCaptchaCode(),
                httpRequest
        );
        ResponseCookie refreshCookie = result.refreshCookie();
        response.addHeader("Set-Cookie", refreshCookie.toString());
        return Result.ok(new LoginResponse(result.accessToken()));
    }

    @PostMapping("/refresh")
    public Result<LoginResponse> refresh(HttpServletRequest request, HttpServletResponse response) {
        AuthService.RefreshResult result = authService.refresh(request);
        response.addHeader("Set-Cookie", result.refreshCookie().toString());
        return Result.ok(new LoginResponse(result.accessToken()));
    }

    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        authService.logout(request);
        response.addHeader("Set-Cookie", authService.clearRefreshCookie().toString());
        return Result.ok();
    }

    @GetMapping("/me")
    public Result<MeResponse> me(Authentication authentication) {
        var jwt = CurrentUser.requireJwt(authentication);
        MeResponse me = new MeResponse();
        me.setUserId(Integer.parseInt(jwt.getSubject()));
        me.setUsername(jwt.getClaimAsString("username"));
        List<String> authorities = jwt.getClaimAsStringList("authorities");
        me.setAuthorities(authorities == null ? List.of() : authorities);
        return Result.ok(me);
    }

    @PostMapping("/register")
    public Result<RegisterResponse> register(@Valid @RequestBody RegisterRequest request, HttpServletRequest httpRequest) {
        return Result.ok(registrationService.register(request, httpRequest));
    }

    @PostMapping("/register/code/resend")
    public Result<RegisterCodeResendResponse> resendRegisterCode(@Valid @RequestBody RegisterCodeResendRequest request) {
        return Result.ok(registrationVerificationService.resendCode(
                request.getUserId(),
                request.getCaptchaId(),
                request.getCaptchaCode()
        ));
    }

    @PostMapping("/register/code/verify")
    public Result<LoginResponse> verifyRegisterCode(@Valid @RequestBody RegisterCodeVerifyRequest request, HttpServletResponse response) {
        AuthService.LoginResult result = registrationVerificationService.verifyAndLogin(request.getUserId(), request.getCode());
        response.addHeader(HttpHeaders.SET_COOKIE, result.refreshCookie().toString());
        return Result.ok(new LoginResponse(result.accessToken()));
    }

    @GetMapping("/captcha")
    public Result<CaptchaIssueResponse> captcha(HttpServletResponse response) {
        CaptchaService.IssuedCaptcha issued = captchaService.issue();
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate, max-age=0");
        response.setHeader(HttpHeaders.PRAGMA, "no-cache");
        return Result.ok(new CaptchaIssueResponse(issued.captchaId(), issued.imageBase64(), issued.ttlSeconds()));
    }

    @PostMapping("/captcha/verify")
    public Result<Boolean> verifyCaptcha(@Valid @RequestBody CaptchaVerifyRequest request) {
        return Result.ok(captchaService.verify(request.getCaptchaId(), request.getCode()));
    }

    @PostMapping("/password/reset/request")
    public Result<PasswordResetRequestResponse> requestPasswordReset(@Valid @RequestBody PasswordResetRequestRequest request) {
        PasswordResetService.RequestResult result = passwordResetService.requestReset(
                request.getEmail(),
                request.getCaptchaId(),
                request.getCaptchaCode()
        );
        return Result.ok(new PasswordResetRequestResponse(result.issued(), result.resetLink()));
    }

    @PostMapping("/password/reset/confirm")
    public Result<Boolean> confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmRequest request) {
        boolean ok = passwordResetService.confirmReset(
                request.getResetToken(),
                request.getNewPassword(),
                request.getCaptchaId(),
                request.getCaptchaCode()
        );
        return Result.ok(ok);
    }
}
