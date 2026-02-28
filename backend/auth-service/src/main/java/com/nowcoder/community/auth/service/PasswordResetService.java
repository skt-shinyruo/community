package com.nowcoder.community.auth.service;

import com.nowcoder.community.auth.config.PasswordResetProperties;
import com.nowcoder.community.auth.api.AuthErrorCode;
import com.nowcoder.community.contracts.api.CommonErrorCode;
import com.nowcoder.community.contracts.exception.BusinessException;
import com.nowcoder.community.user.api.rpc.dto.UserInternalUserByEmailResponse;
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

    public PasswordResetService(
            PasswordResetProperties properties,
            PasswordResetTokenStore tokenStore,
            UserServiceInternalClient userServiceInternalClient,
            MailService mailService,
            CaptchaService captchaService
    ) {
        this.properties = properties;
        this.tokenStore = tokenStore;
        this.userServiceInternalClient = userServiceInternalClient;
        this.mailService = mailService;
        this.captchaService = captchaService;
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

        // 先做配置校验：避免“部分邮箱成功/部分失败”导致用户枚举；也避免签发 token 后才发现链接无法生成。
        String resetBaseUrl = normalizeResetBaseUrlOrThrow();

        String normalizedEmail = email.trim();
        UserInternalUserByEmailResponse user = userServiceInternalClient.findByEmailOrNull(normalizedEmail);
        if (user == null || user.getUserId() <= 0 || user.getStatus() == 0) {
            // 防用户枚举：邮箱不存在/未激活等情况也返回“已发送”（但不实际下发 token/邮件）
            return new RequestResult(true, "");
        }

        String token = uuid();
        Duration ttl = Duration.ofSeconds(Math.max(60, properties.getTtlSeconds()));
        tokenStore.store(token, user.getUserId(), ttl);

        String resetLink = buildResetLink(resetBaseUrl, token);
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

    private String normalizeResetBaseUrlOrThrow() {
        String base = properties.getResetBaseUrl();
        if (!StringUtils.hasText(base)) {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR,
                    "未配置 auth.password-reset.reset-base-url，无法生成重置密码链接");
        }
        String normalized = base.trim();
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private String buildResetLink(String resetBaseUrl, String token) {
        return resetBaseUrl + "/#/auth/password/reset?token=" + token;
    }

    private String uuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public record RequestResult(boolean issued, String resetLink) {
    }
}
