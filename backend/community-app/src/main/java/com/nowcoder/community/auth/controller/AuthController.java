package com.nowcoder.community.auth.controller;

import com.nowcoder.community.auth.application.CaptchaApplicationService;
import com.nowcoder.community.auth.application.LoginApplicationService;
import com.nowcoder.community.auth.application.PasswordResetApplicationService;
import com.nowcoder.community.auth.application.RegistrationApplicationService;
import com.nowcoder.community.auth.application.RegistrationVerificationApplicationService;
import com.nowcoder.community.auth.application.command.ConfirmPasswordResetCommand;
import com.nowcoder.community.auth.application.command.IssueCaptchaCommand;
import com.nowcoder.community.auth.application.command.LoginCommand;
import com.nowcoder.community.auth.application.command.LogoutCommand;
import com.nowcoder.community.auth.application.command.RefreshCommand;
import com.nowcoder.community.auth.application.command.RegisterCommand;
import com.nowcoder.community.auth.application.command.RequestPasswordResetCommand;
import com.nowcoder.community.auth.application.command.ResendRegisterCodeCommand;
import com.nowcoder.community.auth.application.command.VerifyRegisterCodeCommand;
import com.nowcoder.community.auth.application.result.CaptchaIssueResult;
import com.nowcoder.community.auth.application.result.LoginResult;
import com.nowcoder.community.auth.application.result.RefreshCookieSpec;
import com.nowcoder.community.auth.application.result.PasswordResetRequestResult;
import com.nowcoder.community.auth.application.result.RefreshResult;
import com.nowcoder.community.auth.application.result.RegisterCodeResendResult;
import com.nowcoder.community.auth.application.result.RegisterResult;
import com.nowcoder.community.auth.controller.dto.CaptchaIssueResponse;
import com.nowcoder.community.auth.controller.dto.LoginRequest;
import com.nowcoder.community.auth.controller.dto.LoginResponse;
import com.nowcoder.community.auth.controller.dto.MeResponse;
import com.nowcoder.community.auth.controller.dto.RegisterCodeResendRequest;
import com.nowcoder.community.auth.controller.dto.RegisterCodeResendResponse;
import com.nowcoder.community.auth.controller.dto.RegisterCodeVerifyRequest;
import com.nowcoder.community.auth.controller.dto.RegisterRequest;
import com.nowcoder.community.auth.controller.dto.RegisterResponse;
import com.nowcoder.community.auth.controller.dto.PasswordResetConfirmRequest;
import com.nowcoder.community.auth.controller.dto.PasswordResetRequestRequest;
import com.nowcoder.community.auth.controller.dto.PasswordResetRequestResponse;
import com.nowcoder.community.auth.exception.AuthErrorCode;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.common.web.net.ClientIpResolver;
import com.nowcoder.community.infra.security.auth.CurrentUser;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.util.StringUtils;
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

    private final LoginApplicationService loginApplicationService;
    private final RegistrationApplicationService registrationApplicationService;
    private final RegistrationVerificationApplicationService registrationVerificationApplicationService;
    private final CaptchaApplicationService captchaApplicationService;
    private final PasswordResetApplicationService passwordResetApplicationService;
    private final ClientIpResolver clientIpResolver;

    public AuthController(
            LoginApplicationService loginApplicationService,
            RegistrationApplicationService registrationApplicationService,
            RegistrationVerificationApplicationService registrationVerificationApplicationService,
            CaptchaApplicationService captchaApplicationService,
            PasswordResetApplicationService passwordResetApplicationService,
            ClientIpResolver clientIpResolver
    ) {
        this.loginApplicationService = loginApplicationService;
        this.registrationApplicationService = registrationApplicationService;
        this.registrationVerificationApplicationService = registrationVerificationApplicationService;
        this.captchaApplicationService = captchaApplicationService;
        this.passwordResetApplicationService = passwordResetApplicationService;
        this.clientIpResolver = clientIpResolver;
    }

    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse response) {
        ClientIpResolver.ResolvedClientIp resolvedIp = clientIpResolver.resolve(httpRequest);
        LoginResult result = loginApplicationService.login(new LoginCommand(
                request.getUsername(),
                request.getPassword(),
                request.getCaptchaId(),
                request.getCaptchaCode(),
                resolvedIp == null ? null : resolvedIp.ip(),
                resolvedIp == null ? null : resolvedIp.source()
        ));
        addRefreshCookie(response, result.refreshCookie());
        return Result.ok(new LoginResponse(result.accessToken()));
    }

    @PostMapping("/refresh")
    public Result<LoginResponse> refresh(HttpServletRequest request, HttpServletResponse response) {
        try {
            RefreshResult result = loginApplicationService.refresh(new RefreshCommand(readRefreshToken(request)));
            addRefreshCookie(response, result.refreshCookie());
            return Result.ok(new LoginResponse(result.accessToken()));
        } catch (BusinessException ex) {
            if (shouldClearRefreshCookie(ex)) {
                addRefreshCookie(response, loginApplicationService.clearRefreshCookie());
            }
            throw ex;
        }
    }

    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        loginApplicationService.logout(new LogoutCommand(readRefreshToken(request)));
        addRefreshCookie(response, loginApplicationService.clearRefreshCookie());
        return Result.ok();
    }

    @GetMapping("/me")
    public Result<MeResponse> me(Authentication authentication) {
        var jwt = CurrentUser.requireJwt(authentication);
        MeResponse me = new MeResponse();
        me.setUserId(parseUserUuidOrThrow(jwt.getSubject()));
        me.setUsername(jwt.getClaimAsString("username"));
        List<String> authorities = jwt.getClaimAsStringList("authorities");
        me.setAuthorities(authorities == null ? List.of() : authorities);
        return Result.ok(me);
    }

    @PostMapping("/register")
    public Result<RegisterResponse> register(@Valid @RequestBody RegisterRequest request, HttpServletRequest httpRequest) {
        return Result.ok(toResponse(registrationApplicationService.register(new RegisterCommand(
                request.getUsername(),
                request.getPassword(),
                request.getEmail(),
                request.getCaptchaId(),
                request.getCaptchaCode()
        ))));
    }

    @PostMapping("/register/code/resend")
    public Result<RegisterCodeResendResponse> resendRegisterCode(@Valid @RequestBody RegisterCodeResendRequest request) {
        return Result.ok(toResponse(registrationVerificationApplicationService.resendCode(new ResendRegisterCodeCommand(
                request.getRegistrationToken(),
                request.getCaptchaId(),
                request.getCaptchaCode()
        ))));
    }

    @PostMapping("/register/code/verify")
    public Result<LoginResponse> verifyRegisterCode(@Valid @RequestBody RegisterCodeVerifyRequest request, HttpServletResponse response) {
        LoginResult result = registrationVerificationApplicationService.verifyAndLogin(new VerifyRegisterCodeCommand(
                request.getRegistrationToken(),
                request.getCode()
        ));
        addRefreshCookie(response, result.refreshCookie());
        return Result.ok(new LoginResponse(result.accessToken()));
    }

    @GetMapping("/captcha")
    public Result<CaptchaIssueResponse> captcha(HttpServletRequest request, HttpServletResponse response) {
        ClientIpResolver.ResolvedClientIp resolvedIp = clientIpResolver.resolve(request);
        CaptchaIssueResult result = captchaApplicationService.issue(new IssueCaptchaCommand(
                resolvedIp == null ? null : resolvedIp.ip()
        ));
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate, max-age=0");
        response.setHeader(HttpHeaders.PRAGMA, "no-cache");
        return Result.ok(new CaptchaIssueResponse(result.captchaId(), result.imageBase64(), result.ttlSeconds()));
    }

    @PostMapping("/password/reset/request")
    public Result<PasswordResetRequestResponse> requestPasswordReset(
            @Valid @RequestBody PasswordResetRequestRequest request,
            HttpServletRequest httpRequest
    ) {
        ClientIpResolver.ResolvedClientIp resolvedIp = clientIpResolver.resolve(httpRequest);
        PasswordResetRequestResult result = passwordResetApplicationService.requestReset(new RequestPasswordResetCommand(
                request.getEmail(),
                request.getCaptchaId(),
                request.getCaptchaCode(),
                resolvedIp == null ? null : resolvedIp.ip()
        ));
        return Result.ok(new PasswordResetRequestResponse(result.issued(), result.resetLink()));
    }

    @PostMapping("/password/reset/confirm")
    public Result<Boolean> confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmRequest request) {
        return Result.ok(passwordResetApplicationService.confirmReset(new ConfirmPasswordResetCommand(
                request.getResetToken(),
                request.getNewPassword(),
                request.getCaptchaId(),
                request.getCaptchaCode()
        )));
    }

    private String readRefreshToken(HttpServletRequest request) {
        return readCookie(request, loginApplicationService.refreshCookieName());
    }

    private void addRefreshCookie(HttpServletResponse response, RefreshCookieSpec spec) {
        if (response == null || spec == null) {
            return;
        }
        response.addHeader(HttpHeaders.SET_COOKIE, toResponseCookie(spec).toString());
    }

    private ResponseCookie toResponseCookie(RefreshCookieSpec spec) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(spec.name(), spec.value())
                .httpOnly(spec.httpOnly())
                .secure(spec.secure())
                .path(spec.path())
                .maxAge(spec.maxAgeSeconds());
        if (StringUtils.hasText(spec.sameSite())) {
            builder.sameSite(spec.sameSite());
        }
        return builder.build();
    }

    private String readCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request == null ? null : request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (cookie != null && name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private boolean shouldClearRefreshCookie(BusinessException ex) {
        if (ex == null || ex.getErrorCode() == null) {
            return false;
        }
        int code = ex.getErrorCode().getCode();
        return code == AuthErrorCode.USER_DISABLED.getCode()
                || code == AuthErrorCode.REFRESH_TOKEN_INVALID.getCode();
    }

    private RegisterResponse toResponse(RegisterResult result) {
        RegisterResponse response = new RegisterResponse();
        response.setUserId(result.userId());
        response.setRegistrationToken(result.registrationToken());
        response.setEmailCodeIssued(result.emailCodeIssued());
        response.setMaskedEmail(result.maskedEmail());
        response.setDebugEmailCode(result.debugEmailCode());
        return response;
    }

    private RegisterCodeResendResponse toResponse(RegisterCodeResendResult result) {
        RegisterCodeResendResponse response = new RegisterCodeResendResponse();
        response.setIssued(result.issued());
        response.setMaskedEmail(result.maskedEmail());
        response.setDebugEmailCode(result.debugEmailCode());
        return response;
    }

    private UUID parseUserUuidOrThrow(String subject) {
        try {
            return UUID.fromString(subject);
        } catch (RuntimeException ex) {
            throw new BusinessException(AuthErrorCode.TOKEN_INVALID);
        }
    }
}
