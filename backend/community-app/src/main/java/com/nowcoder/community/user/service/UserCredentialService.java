package com.nowcoder.community.user.service;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.exception.CommonErrorCode;
import com.nowcoder.community.user.api.action.UserCredentialActionApi;
import com.nowcoder.community.user.api.model.UserAuthenticationResultView;
import com.nowcoder.community.user.api.model.UserCredentialView;
import com.nowcoder.community.user.api.query.UserCredentialQueryApi;
import com.nowcoder.community.user.entity.User;
import com.nowcoder.community.user.mapper.UserMapper;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;
import static com.nowcoder.community.user.exception.UserErrorCode.USER_NOT_FOUND;

@Service
public class UserCredentialService implements UserCredentialQueryApi, UserCredentialActionApi {

    private final UserMapper userMapper;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public UserCredentialService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public UserAuthenticationResultView authenticate(String username, String password) {
        String trimmedUsername = safeTrim(username);
        String trimmedPassword = safeTrim(password);
        if (!StringUtils.hasText(trimmedUsername) || !StringUtils.hasText(trimmedPassword)) {
            return UserAuthenticationResultView.invalidCredentials();
        }

        User user = userMapper.selectByName(trimmedUsername);
        if (user == null) {
            return UserAuthenticationResultView.invalidCredentials();
        }
        if (user.getStatus() == 0) {
            return UserAuthenticationResultView.userDisabled(toCredentialView(user));
        }

        if (!passwordMatches(user, trimmedPassword)) {
            return UserAuthenticationResultView.invalidCredentials();
        }

        if (isLegacyPassword(user)) {
            String bcrypt = passwordEncoder.encode(trimmedPassword);
            userMapper.updatePassword(user.getId(), bcrypt);
            user.setPassword(bcrypt);
        }

        return UserAuthenticationResultView.authenticated(toCredentialView(user));
    }

    @Override
    public UserCredentialView getByUserId(int userId) {
        if (userId <= 0) {
            throw new BusinessException(INVALID_ARGUMENT, "userId 非法");
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(USER_NOT_FOUND);
        }
        return toCredentialView(user);
    }

    @Override
    public UserCredentialView findByEmailOrNull(String email) {
        String value = safeTrim(email);
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(INVALID_ARGUMENT, "email 不能为空");
        }
        return toCredentialView(userMapper.selectByEmail(value));
    }

    @Override
    public void updatePassword(int userId, String newPassword) {
        if (userId <= 0) {
            throw new BusinessException(INVALID_ARGUMENT, "userId 非法");
        }
        String trimmedPassword = safeTrim(newPassword);
        if (!StringUtils.hasText(trimmedPassword)) {
            throw new BusinessException(INVALID_ARGUMENT, "newPassword 不能为空");
        }

        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(USER_NOT_FOUND);
        }

        String bcrypt = passwordEncoder.encode(trimmedPassword);
        int updated = userMapper.updatePassword(userId, bcrypt);
        if (updated <= 0) {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR, "更新密码失败");
        }
    }

    @Override
    public List<String> authoritiesOf(UserCredentialView user) {
        return user == null ? List.of() : authoritiesForType(user.type());
    }

    private List<String> authoritiesForType(int type) {
        if (type == 1) {
            return List.of("ROLE_ADMIN");
        }
        if (type == 2) {
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

        if (isBcrypt(stored)) {
            try {
                return passwordEncoder.matches(rawPassword, stored);
            } catch (RuntimeException ignored) {
                return false;
            }
        }

        String salt = user.getSalt();
        if (!StringUtils.hasText(salt)) {
            return false;
        }
        return stored.equals(md5(rawPassword + salt));
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
        return stored.startsWith("$2a$") || stored.startsWith("$2b$") || stored.startsWith("$2y$");
    }

    private UserCredentialView toCredentialView(User user) {
        if (user == null || user.getId() <= 0) {
            return null;
        }
        return new UserCredentialView(user.getId(), user.getUsername(), user.getStatus(), user.getType(), user.getHeaderUrl());
    }

    private String md5(String input) {
        return DigestUtils.md5DigestAsHex(input.getBytes(StandardCharsets.UTF_8));
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }
}
