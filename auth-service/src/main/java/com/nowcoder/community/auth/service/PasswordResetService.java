package com.nowcoder.community.auth.service;

import com.nowcoder.community.auth.config.PasswordResetProperties;
import com.nowcoder.community.auth.config.RegistrationProperties;
import com.nowcoder.community.common.api.AuthErrorCode;
import com.nowcoder.community.common.api.CommonErrorCode;
import com.nowcoder.community.common.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.UUID;

@Service
public class PasswordResetService {

    private final PasswordResetProperties properties;
    private final PasswordResetTokenStore tokenStore;
    private final UserServiceInternalClient userServiceInternalClient;
    private final MailService mailService;
    private final CaptchaService captchaService;
    private final RegistrationProperties registrationProperties;

    public PasswordResetService(
            PasswordResetProperties properties,
            PasswordResetTokenStore tokenStore,
            UserServiceInternalClient userServiceInternalClient,
            MailService mailService,
            CaptchaService captchaService,
            RegistrationProperties registrationProperties
    ) {
        this.properties = properties;
        this.tokenStore = tokenStore;
        this.userServiceInternalClient = userServiceInternalClient;
        this.mailService = mailService;
        this.captchaService = captchaService;
        this.registrationProperties = registrationProperties;
    }

    public RequestResult requestReset(String email, String captchaId, String captchaCode) {
        if (!StringUtils.hasText(email)) {
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT, "email 不能为空");
        }
        if (!StringUtils.hasText(captchaId) || !StringUtils.hasText(captchaCode)) {
            throw new BusinessException(AuthErrorCode.CAPTCHA_REQUIRED);
        }
        if (!captchaService.verify(captchaId, captchaCode)) {
            throw new BusinessException(AuthErrorCode.CAPTCHA_INVALID);
        }

        String normalizedEmail = email.trim();
        com.nowcoder.community.auth.service.dto.UserInternalUserByEmailResponse user = userServiceInternalClient.findByEmailOrNull(normalizedEmail);
        if (user == null || user.getUserId() <= 0 || user.getStatus() == 0) {
            // 防用户枚举：邮箱不存在/未激活等情况也返回“已发送”（但不实际下发 token/邮件）
            return new RequestResult(true, "");
        }

        String token = uuid();
        Duration ttl = Duration.ofSeconds(Math.max(60, properties.getTtlSeconds()));
        tokenStore.store(token, user.getUserId(), ttl);

        String resetLink = buildResetLink(token);
        mailService.sendPasswordResetMail(user.getEmail(), resetLink);

        if (properties.isExposeResetLink()) {
            return new RequestResult(true, resetLink);
        }
        return new RequestResult(true, "");
    }

    public boolean confirmReset(String resetToken, String newPassword, String captchaId, String captchaCode) {
        if (!StringUtils.hasText(resetToken) || !StringUtils.hasText(newPassword)) {
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT, "resetToken/newPassword 不能为空");
        }
        if (!StringUtils.hasText(captchaId) || !StringUtils.hasText(captchaCode)) {
            throw new BusinessException(AuthErrorCode.CAPTCHA_REQUIRED);
        }
        if (!captchaService.verify(captchaId, captchaCode)) {
            throw new BusinessException(AuthErrorCode.CAPTCHA_INVALID);
        }

        Integer userId = tokenStore.consume(resetToken.trim());
        if (userId == null || userId <= 0) {
            throw new BusinessException(AuthErrorCode.PASSWORD_RESET_INVALID);
        }

        userServiceInternalClient.updatePassword(userId, newPassword.trim());
        return true;
    }

    private String buildResetLink(String token) {
        String base = registrationProperties.getActivationBaseUrl();
        if (!StringUtils.hasText(base)) {
            base = "http://localhost:8080";
        }
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/#/auth/password/reset?token=" + token;
    }

    private String uuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public record RequestResult(boolean issued, String resetLink) {
    }
}
