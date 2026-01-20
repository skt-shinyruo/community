package com.nowcoder.community.user.service;

import com.nowcoder.community.common.api.AuthErrorCode;
import com.nowcoder.community.common.api.CommonErrorCode;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.user.dao.UserMapper;
import com.nowcoder.community.user.entity.User;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static com.nowcoder.community.common.api.CommonErrorCode.INVALID_ARGUMENT;

@Service
public class InternalUserService {

    public static final int ACTIVATION_SUCCESS = 0;
    public static final int ACTIVATION_REPEAT = 1;
    public static final int ACTIVATION_FAILURE = 2;

    private final UserMapper userMapper;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public InternalUserService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    public User authenticate(String username, String password) {
        String u = safeTrim(username);
        String p = safeTrim(password);
        if (!StringUtils.hasText(u) || !StringUtils.hasText(p)) {
            throw new BusinessException(AuthErrorCode.LOGIN_FAILED);
        }

        User user = userMapper.selectByName(u);
        if (user == null) {
            throw new BusinessException(AuthErrorCode.LOGIN_FAILED);
        }
        if (user.getStatus() == 0) {
            throw new BusinessException(AuthErrorCode.USER_DISABLED);
        }

        if (!passwordMatches(user, p)) {
            throw new BusinessException(AuthErrorCode.LOGIN_FAILED);
        }

        // 渐进 rehash：legacy(MD5+salt) -> bcrypt（仅在校验通过后触发）
        if (isLegacyPassword(user)) {
            String bcrypt = passwordEncoder.encode(p);
            userMapper.updatePassword(user.getId(), bcrypt);
            user.setPassword(bcrypt);
        }

        return user;
    }

    public User getSessionProfile(int userId) {
        if (userId <= 0) {
            throw new BusinessException(INVALID_ARGUMENT, "userId 非法");
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "用户不存在");
        }
        return user;
    }

    public User findByEmailOrNull(String email) {
        String e = safeTrim(email);
        if (!StringUtils.hasText(e)) {
            throw new BusinessException(INVALID_ARGUMENT, "email 不能为空");
        }
        return userMapper.selectByEmail(e);
    }

    public User register(String username, String password, String email) {
        String u = safeTrim(username);
        String p = safeTrim(password);
        String e = safeTrim(email);

        if (!StringUtils.hasText(u) || !StringUtils.hasText(p) || !StringUtils.hasText(e)) {
            throw new BusinessException(INVALID_ARGUMENT, "用户名/密码/邮箱不能为空");
        }

        if (userMapper.selectByName(u) != null) {
            throw new BusinessException(INVALID_ARGUMENT, "该账号已存在");
        }
        if (userMapper.selectByEmail(e) != null) {
            throw new BusinessException(INVALID_ARGUMENT, "该邮箱已被注册");
        }

        User user = new User();
        user.setUsername(u);
        user.setPassword(passwordEncoder.encode(p));
        user.setSalt("");
        user.setEmail(e);
        user.setType(0);
        user.setStatus(0);
        user.setActivationCode(uuid());
        user.setHeaderUrl(String.format("http://images.nowcoder.com/head/%dt.png", new Random().nextInt(1000)));
        user.setCreateTime(new Date());

        userMapper.insertUser(user);
        if (user.getId() <= 0) {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR, "创建用户失败");
        }
        return user;
    }

    public int activate(int userId, String activationCode) {
        if (userId <= 0 || !StringUtils.hasText(activationCode)) {
            return ACTIVATION_FAILURE;
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            return ACTIVATION_FAILURE;
        }
        if (user.getStatus() == 1) {
            return ACTIVATION_REPEAT;
        }
        if (activationCode.equals(user.getActivationCode())) {
            userMapper.updateStatus(userId, 1);
            return ACTIVATION_SUCCESS;
        }
        return ACTIVATION_FAILURE;
    }

    public void updatePassword(int userId, String newPassword) {
        if (userId <= 0) {
            throw new BusinessException(INVALID_ARGUMENT, "userId 非法");
        }
        String p = safeTrim(newPassword);
        if (!StringUtils.hasText(p)) {
            throw new BusinessException(INVALID_ARGUMENT, "newPassword 不能为空");
        }

        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "用户不存在");
        }

        String bcrypt = passwordEncoder.encode(p);
        int updated = userMapper.updatePassword(userId, bcrypt);
        if (updated <= 0) {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR, "更新密码失败");
        }
    }

    public List<String> authoritiesOf(User user) {
        if (user == null) {
            return List.of();
        }
        if (user.getType() == 1) {
            return List.of("ROLE_ADMIN");
        }
        if (user.getType() == 2) {
            return List.of("ROLE_MODERATOR");
        }
        return List.of("ROLE_USER");
    }

    private boolean passwordMatches(User user, String rawPassword) {
        if (user == null || !StringUtils.hasText(rawPassword)) {
            return false;
        }
        String stored = user.getPassword();
        if (!StringUtils.hasText(stored)) {
            return false;
        }

        // bcrypt: password 字段自带盐与参数，salt 字段忽略即可
        if (isBcrypt(stored)) {
            try {
                return passwordEncoder.matches(rawPassword, stored);
            } catch (Exception ignored) {
                return false;
            }
        }

        // legacy: MD5(password + salt)
        String salt = user.getSalt();
        if (!StringUtils.hasText(salt)) {
            return false;
        }
        String legacy = md5(rawPassword + salt);
        return stored.equals(legacy);
    }

    private boolean isLegacyPassword(User user) {
        if (user == null) {
            return false;
        }
        String stored = user.getPassword();
        return StringUtils.hasText(stored) && !isBcrypt(stored);
    }

    private boolean isBcrypt(String stored) {
        if (!StringUtils.hasText(stored)) {
            return false;
        }
        // $2a$ / $2b$ / $2y$...
        return stored.startsWith("$2a$") || stored.startsWith("$2b$") || stored.startsWith("$2y$");
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

