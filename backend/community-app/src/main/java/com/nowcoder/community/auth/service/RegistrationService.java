package com.nowcoder.community.auth.service;

import com.nowcoder.community.auth.dto.RegisterRequest;
import com.nowcoder.community.auth.dto.RegisterResponse;
import com.nowcoder.community.auth.config.RegistrationProperties;
import com.nowcoder.community.auth.exception.AuthErrorCode;
import com.nowcoder.community.common.exception.CommonErrorCode;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.user.entity.User;
import com.nowcoder.community.user.service.InternalUserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class RegistrationService {

    public static final int ACTIVATION_SUCCESS = 0;
    public static final int ACTIVATION_REPEAT = 1;
    public static final int ACTIVATION_FAILURE = 2;

    private final InternalUserService internalUserService;
    private final RegistrationProperties properties;
    private final MailService mailService;
    private final CaptchaService captchaService;

    public RegistrationService(InternalUserService internalUserService, RegistrationProperties properties, MailService mailService, CaptchaService captchaService) {
        this.internalUserService = internalUserService;
        this.properties = properties;
        this.mailService = mailService;
        this.captchaService = captchaService;
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

        // 先做配置校验：避免创建用户后才发现无法生成激活链接，造成“已创建但无法激活”的隐蔽失败。
        String activationBaseUrl = normalizeActivationBaseUrlOrThrow();

        User created = internalUserService.register(username, password, email);
        if (created == null || created.getId() <= 0 || !StringUtils.hasText(created.getActivationCode())) {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR, "创建用户失败");
        }

        String activationLink = buildActivationLink(activationBaseUrl, created.getId(), created.getActivationCode());
        mailService.sendActivationMail(email, activationLink);

        RegisterResponse resp = new RegisterResponse();
        resp.setUserId(created.getId());
        resp.setActivationIssued(true);
        if (properties.isExposeActivationLink()) {
            resp.setActivationLink(activationLink);
        }
        return resp;
    }

    public int activate(int userId, String code) {
        return internalUserService.activate(userId, code);
    }

    private String normalizeActivationBaseUrlOrThrow() {
        String base = properties.getActivationBaseUrl();
        if (!StringUtils.hasText(base)) {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR,
                    "未配置 auth.registration.activation-base-url，无法生成激活链接");
        }
        String normalized = base.trim();
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private String buildActivationLink(String activationBaseUrl, int userId, String activationCode) {
        return activationBaseUrl + "/api/auth/activation/" + userId + "/" + activationCode;
    }

    private String safeTrim(String s) {
        return s == null ? "" : s.trim();
    }
}
