package com.nowcoder.community.auth.service;

import com.nowcoder.community.auth.api.dto.RegisterRequest;
import com.nowcoder.community.auth.api.dto.RegisterResponse;
import com.nowcoder.community.auth.config.RegistrationProperties;
import com.nowcoder.community.common.api.AuthErrorCode;
import com.nowcoder.community.common.api.CommonErrorCode;
import com.nowcoder.community.common.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class RegistrationService {

    public static final int ACTIVATION_SUCCESS = 0;
    public static final int ACTIVATION_REPEAT = 1;
    public static final int ACTIVATION_FAILURE = 2;

    private final UserServiceInternalClient userServiceInternalClient;
    private final RegistrationProperties properties;
    private final MailService mailService;
    private final CaptchaService captchaService;

    public RegistrationService(UserServiceInternalClient userServiceInternalClient, RegistrationProperties properties, MailService mailService, CaptchaService captchaService) {
        this.userServiceInternalClient = userServiceInternalClient;
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

        com.nowcoder.community.auth.service.dto.UserInternalRegisterResponse created = userServiceInternalClient.register(username, password, email);
        if (created == null || created.getUserId() <= 0 || !StringUtils.hasText(created.getActivationCode())) {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR, "创建用户失败");
        }

        String activationLink = buildActivationLink(created.getUserId(), created.getActivationCode());
        mailService.sendActivationMail(email, activationLink);

        RegisterResponse resp = new RegisterResponse();
        resp.setUserId(created.getUserId());
        resp.setActivationIssued(true);
        if (properties.isExposeActivationLink()) {
            resp.setActivationLink(activationLink);
        }
        return resp;
    }

    public int activate(int userId, String code) {
        return userServiceInternalClient.activate(userId, code);
    }

    private String buildActivationLink(int userId, String activationCode) {
        String base = properties.getActivationBaseUrl();
        if (!StringUtils.hasText(base)) {
            base = "http://localhost:8080";
        }
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/api/auth/activation/" + userId + "/" + activationCode;
    }

    private String safeTrim(String s) {
        return s == null ? "" : s.trim();
    }
}
