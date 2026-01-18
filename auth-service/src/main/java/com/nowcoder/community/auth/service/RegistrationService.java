package com.nowcoder.community.auth.service;

import com.nowcoder.community.auth.api.dto.RegisterRequest;
import com.nowcoder.community.auth.api.dto.RegisterResponse;
import com.nowcoder.community.auth.config.RegistrationProperties;
import com.nowcoder.community.auth.user.User;
import com.nowcoder.community.auth.user.UserMapper;
import com.nowcoder.community.common.api.AuthErrorCode;
import com.nowcoder.community.common.api.CommonErrorCode;
import com.nowcoder.community.common.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Random;
import java.util.UUID;

@Service
public class RegistrationService {

    public static final int ACTIVATION_SUCCESS = 0;
    public static final int ACTIVATION_REPEAT = 1;
    public static final int ACTIVATION_FAILURE = 2;

    private final UserMapper userMapper;
    private final RegistrationProperties properties;
    private final MailService mailService;
    private final CaptchaService captchaService;

    public RegistrationService(UserMapper userMapper, RegistrationProperties properties, MailService mailService, CaptchaService captchaService) {
        this.userMapper = userMapper;
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

        if (userMapper.selectByName(username) != null) {
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT, "该账号已存在");
        }
        if (userMapper.selectByEmail(email) != null) {
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT, "该邮箱已被注册");
        }

        User user = new User();
        user.setUsername(username);
        user.setSalt(uuid().substring(0, 5));
        user.setPassword(md5(password + user.getSalt()));
        user.setEmail(email);
        user.setType(0);
        user.setStatus(0);
        user.setActivationCode(uuid());
        user.setHeaderUrl(String.format("http://images.nowcoder.com/head/%dt.png", new Random().nextInt(1000)));
        user.setCreateTime(new Date());

        userMapper.insertUser(user);
        if (user.getId() <= 0) {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR, "创建用户失败");
        }

        String activationLink = buildActivationLink(user.getId(), user.getActivationCode());
        mailService.sendActivationMail(user.getEmail(), activationLink);

        RegisterResponse resp = new RegisterResponse();
        resp.setUserId(user.getId());
        resp.setActivationIssued(true);
        if (properties.isExposeActivationLink()) {
            resp.setActivationLink(activationLink);
        }
        return resp;
    }

    public int activate(int userId, String code) {
        if (userId <= 0 || !StringUtils.hasText(code)) {
            return ACTIVATION_FAILURE;
        }

        User user = userMapper.selectById(userId);
        if (user == null) {
            return ACTIVATION_FAILURE;
        }
        if (user.getStatus() == 1) {
            return ACTIVATION_REPEAT;
        }
        if (code.equals(user.getActivationCode())) {
            userMapper.updateStatus(userId, 1);
            return ACTIVATION_SUCCESS;
        }
        return ACTIVATION_FAILURE;
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

    private String md5(String input) {
        return DigestUtils.md5DigestAsHex(input.getBytes(StandardCharsets.UTF_8));
    }

    private String uuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private String safeTrim(String s) {
        return s == null ? "" : s.trim();
    }
}
