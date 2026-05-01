package com.nowcoder.community.auth.application;

import com.nowcoder.community.auth.application.command.ConfirmPasswordResetCommand;
import com.nowcoder.community.auth.application.command.IssueCaptchaCommand;
import com.nowcoder.community.auth.application.command.LoginCommand;
import com.nowcoder.community.auth.application.command.LogoutCommand;
import com.nowcoder.community.auth.application.command.RefreshCommand;
import com.nowcoder.community.auth.application.command.RegisterCommand;
import com.nowcoder.community.auth.application.command.RequestPasswordResetCommand;
import com.nowcoder.community.auth.application.command.ResendRegisterCodeCommand;
import com.nowcoder.community.auth.application.command.VerifyCaptchaCommand;
import com.nowcoder.community.auth.application.command.VerifyRegisterCodeCommand;
import com.nowcoder.community.auth.application.result.CaptchaIssueResult;
import com.nowcoder.community.auth.application.result.LoginResult;
import com.nowcoder.community.auth.application.result.PasswordResetRequestResult;
import com.nowcoder.community.auth.application.result.RefreshCookieSpec;
import com.nowcoder.community.auth.application.result.RefreshResult;
import com.nowcoder.community.auth.application.result.RegisterCodeResendResult;
import com.nowcoder.community.auth.application.result.RegisterResult;
import com.nowcoder.community.auth.exception.AuthErrorCode;
import com.nowcoder.community.common.exception.BusinessException;
import org.springframework.stereotype.Service;

@Service
public class AuthApplicationService {

    private final LoginApplicationService loginApplicationService;
    private final RegistrationApplicationService registrationApplicationService;
    private final RegistrationVerificationApplicationService registrationVerificationApplicationService;
    private final CaptchaApplicationService captchaApplicationService;
    private final PasswordResetApplicationService passwordResetApplicationService;

    public AuthApplicationService(
            LoginApplicationService loginApplicationService,
            RegistrationApplicationService registrationApplicationService,
            RegistrationVerificationApplicationService registrationVerificationApplicationService,
            CaptchaApplicationService captchaApplicationService,
            PasswordResetApplicationService passwordResetApplicationService
    ) {
        this.loginApplicationService = loginApplicationService;
        this.registrationApplicationService = registrationApplicationService;
        this.registrationVerificationApplicationService = registrationVerificationApplicationService;
        this.captchaApplicationService = captchaApplicationService;
        this.passwordResetApplicationService = passwordResetApplicationService;
    }

    public LoginResult login(LoginCommand command) {
        return loginApplicationService.login(command);
    }

    public RefreshResult refresh(RefreshCommand command) {
        return loginApplicationService.refresh(command);
    }

    public void logout(LogoutCommand command) {
        loginApplicationService.logout(command);
    }

    public RegisterResult register(RegisterCommand command) {
        return registrationApplicationService.register(command);
    }

    public RegisterCodeResendResult resendRegisterCode(ResendRegisterCodeCommand command) {
        return registrationVerificationApplicationService.resendCode(command);
    }

    public LoginResult verifyRegisterCode(VerifyRegisterCodeCommand command) {
        return registrationVerificationApplicationService.verifyAndLogin(command);
    }

    public CaptchaIssueResult captcha(IssueCaptchaCommand command) {
        return captchaApplicationService.issue(command);
    }

    public boolean verifyCaptcha(VerifyCaptchaCommand command) {
        return captchaApplicationService.verify(command);
    }

    public PasswordResetRequestResult requestPasswordReset(RequestPasswordResetCommand command) {
        return passwordResetApplicationService.requestReset(command);
    }

    public boolean confirmPasswordReset(ConfirmPasswordResetCommand command) {
        return passwordResetApplicationService.confirmReset(command);
    }

    public String refreshCookieName() {
        return loginApplicationService.refreshCookieName();
    }

    public RefreshCookieSpec clearRefreshCookie() {
        return loginApplicationService.clearRefreshCookie();
    }

    public boolean shouldClearRefreshCookie(BusinessException ex) {
        if (ex == null || ex.getErrorCode() == null) {
            return false;
        }
        int code = ex.getErrorCode().getCode();
        return code == AuthErrorCode.USER_DISABLED.getCode()
                || code == AuthErrorCode.REFRESH_TOKEN_INVALID.getCode();
    }
}
