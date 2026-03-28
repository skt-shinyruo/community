package com.nowcoder.community.auth.service;

import com.nowcoder.community.auth.dto.RegisterRequest;
import com.nowcoder.community.auth.dto.RegisterResponse;
import com.nowcoder.community.auth.config.RegistrationProperties;
import com.nowcoder.community.auth.exception.AuthErrorCode;
import com.nowcoder.community.auth.logging.SecurityEventLogger;
import com.nowcoder.community.common.exception.CommonErrorCode;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.user.api.action.UserRegistrationActionApi;
import com.nowcoder.community.user.api.model.PendingRegistrationUserView;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class RegistrationService {

    private static final Logger log = LoggerFactory.getLogger(RegistrationService.class);

    private final UserRegistrationActionApi userRegistrationActionApi;
    private final RegistrationProperties properties;
    private final MailService mailService;
    private final CaptchaService captchaService;
    private final RegistrationCodeStore registrationCodeStore;
    private final RegistrationSessionStore registrationSessionStore;

    public RegistrationService(
            UserRegistrationActionApi userRegistrationActionApi,
            RegistrationProperties properties,
            MailService mailService,
            CaptchaService captchaService,
            RegistrationCodeStore registrationCodeStore,
            RegistrationSessionStore registrationSessionStore
    ) {
        this.userRegistrationActionApi = userRegistrationActionApi;
        this.properties = properties;
        this.mailService = mailService;
        this.captchaService = captchaService;
        this.registrationCodeStore = registrationCodeStore;
        this.registrationSessionStore = registrationSessionStore;
    }

    public RegisterResponse register(RegisterRequest request, HttpServletRequest httpRequest) {
        if (request == null) {
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT, "参数不能为空");
        }
        if (!StringUtils.hasText(request.getCaptchaId()) || !StringUtils.hasText(request.getCaptchaCode())) {
            throw new BusinessException(AuthErrorCode.CAPTCHA_REQUIRED);
        }
        if (!captchaService.verify(request.getCaptchaId(), request.getCaptchaCode())) {
            throw new BusinessException(AuthErrorCode.CAPTCHA_INVALID);
        }

        String username = safeTrim(request.getUsername());
        String password = safeTrim(request.getPassword());
        String email = safeTrim(request.getEmail());

        if (!StringUtils.hasText(username) || !StringUtils.hasText(password) || !StringUtils.hasText(email)) {
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT, "用户名/密码/邮箱不能为空");
        }

        Duration pendingUserTtl = Duration.ofSeconds(Math.max(60, properties.getPendingUser().getTtlSeconds()));
        PendingRegistrationUserView created = userRegistrationActionApi.registerPendingUser(username, password, email, pendingUserTtl);
        if (created == null || created.userId() <= 0) {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR, "创建用户失败");
        }
        String targetEmail = StringUtils.hasText(created.email()) ? created.email() : email;

        String code = generateCode();
        Duration ttl = Duration.ofSeconds(Math.max(60, properties.getCode().getTtlSeconds()));
        Duration cooldown = Duration.ofSeconds(Math.max(0, properties.getCode().getResendCooldownSeconds()));
        RegistrationCodeStore.IssueResult issueResult = registrationCodeStore.issue(created.userId(), code, ttl, cooldown);
        if (issueResult != RegistrationCodeStore.IssueResult.ISSUED) {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR, "注册验证码签发失败");
        }
        mailService.sendRegistrationCodeMail(targetEmail, code);

        RegisterResponse resp = new RegisterResponse();
        resp.setUserId(created.userId());
        String registrationToken = registrationSessionStore == null ? null : registrationSessionStore.issue(created.userId(), pendingUserTtl);
        if (!StringUtils.hasText(registrationToken)) {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR, "注册上下文创建失败");
        }
        resp.setRegistrationToken(registrationToken);
        resp.setEmailCodeIssued(true);
        resp.setMaskedEmail(maskEmail(targetEmail));
        if (properties.getCode().isExposeCode()) {
            resp.setDebugEmailCode(code);
        }
        SecurityEventLogger.info(log, "registration_code_issue", "success",
                "user.id", created.userId(),
                "username", username,
                "masked.email", maskEmail(targetEmail));
        return resp;
    }

    private String generateCode() {
        return Integer.toString(ThreadLocalRandom.current().nextInt(100000, 1000000));
    }

    private String maskEmail(String email) {
        String normalized = safeTrim(email);
        int at = normalized.indexOf('@');
        if (at <= 0) {
            return normalized;
        }
        String local = normalized.substring(0, at);
        String domain = normalized.substring(at);
        if (local.length() <= 1) {
            return "*" + domain;
        }
        if (local.length() == 2) {
            return local.charAt(0) + "*" + domain;
        }
        return local.charAt(0) + "***" + local.charAt(local.length() - 1) + domain;
    }

    private String safeTrim(String s) {
        return s == null ? "" : s.trim();
    }
}
