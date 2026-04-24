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
import com.nowcoder.community.auth.service.AuthApplicationService;
import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.infra.security.auth.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthApplicationService authApplicationService;

    public AuthController(AuthApplicationService authApplicationService) {
        this.authApplicationService = authApplicationService;
    }

    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse response) {
        return Result.ok(authApplicationService.login(request, httpRequest, response));
    }

    @PostMapping("/refresh")
    public Result<LoginResponse> refresh(HttpServletRequest request, HttpServletResponse response) {
        return Result.ok(authApplicationService.refresh(request, response));
    }

    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        authApplicationService.logout(request, response);
        return Result.ok();
    }

    @GetMapping("/me")
    public Result<MeResponse> me(Authentication authentication) {
        var jwt = CurrentUser.requireJwt(authentication);
        MeResponse me = new MeResponse();
        me.setUserId(UUID.fromString(jwt.getSubject()));
        me.setUsername(jwt.getClaimAsString("username"));
        List<String> authorities = jwt.getClaimAsStringList("authorities");
        me.setAuthorities(authorities == null ? List.of() : authorities);
        return Result.ok(me);
    }

    @PostMapping("/register")
    public Result<RegisterResponse> register(@Valid @RequestBody RegisterRequest request, HttpServletRequest httpRequest) {
        return Result.ok(authApplicationService.register(request, httpRequest));
    }

    @PostMapping("/register/code/resend")
    public Result<RegisterCodeResendResponse> resendRegisterCode(@Valid @RequestBody RegisterCodeResendRequest request) {
        return Result.ok(authApplicationService.resendRegisterCode(request));
    }

    @PostMapping("/register/code/verify")
    public Result<LoginResponse> verifyRegisterCode(@Valid @RequestBody RegisterCodeVerifyRequest request, HttpServletResponse response) {
        return Result.ok(authApplicationService.verifyRegisterCode(request, response));
    }

    @GetMapping("/captcha")
    public Result<CaptchaIssueResponse> captcha(HttpServletResponse response) {
        return Result.ok(authApplicationService.captcha(response));
    }

    @PostMapping("/captcha/verify")
    public Result<Boolean> verifyCaptcha(@Valid @RequestBody CaptchaVerifyRequest request) {
        return Result.ok(authApplicationService.verifyCaptcha(request));
    }

    @PostMapping("/password/reset/request")
    public Result<PasswordResetRequestResponse> requestPasswordReset(@Valid @RequestBody PasswordResetRequestRequest request) {
        return Result.ok(authApplicationService.requestPasswordReset(request));
    }

    @PostMapping("/password/reset/confirm")
    public Result<Boolean> confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmRequest request) {
        return Result.ok(authApplicationService.confirmPasswordReset(request));
    }
}
