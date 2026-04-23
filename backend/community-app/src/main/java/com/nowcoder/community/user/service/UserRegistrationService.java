package com.nowcoder.community.user.service;

import com.nowcoder.community.common.id.UuidV7Generator;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.exception.CommonErrorCode;
import com.nowcoder.community.user.api.action.UserRegistrationActionApi;
import com.nowcoder.community.user.api.model.PendingRegistrationUserView;
import com.nowcoder.community.user.api.model.UserCredentialView;
import com.nowcoder.community.user.api.query.UserPendingRegistrationQueryApi;
import com.nowcoder.community.user.contracts.event.UserPolicyChangedPayload;
import com.nowcoder.community.user.entity.User;
import com.nowcoder.community.user.event.UserEventPublisher;
import com.nowcoder.community.user.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;

import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;
import static com.nowcoder.community.user.exception.UserErrorCode.EMAIL_ALREADY_EXISTS;
import static com.nowcoder.community.user.exception.UserErrorCode.USER_ALREADY_EXISTS;
import static com.nowcoder.community.user.exception.UserErrorCode.USER_NOT_FOUND;

@Service
public class UserRegistrationService implements UserRegistrationActionApi, UserPendingRegistrationQueryApi {

    private static final String USERNAME_UNIQUE_CONSTRAINT = "uk_user_username";
    private static final String EMAIL_UNIQUE_CONSTRAINT = "uk_user_email";

    private final UserMapper userMapper;
    private final UuidV7Generator idGenerator;
    private final UserEventPublisher userEventPublisher;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Autowired
    public UserRegistrationService(UserMapper userMapper, UserEventPublisher userEventPublisher) {
        this(userMapper, new UuidV7Generator(), userEventPublisher);
    }

    UserRegistrationService(UserMapper userMapper, UuidV7Generator idGenerator, UserEventPublisher userEventPublisher) {
        this.userMapper = userMapper;
        this.idGenerator = idGenerator;
        this.userEventPublisher = userEventPublisher;
    }

    @Override
    @Transactional
    public PendingRegistrationUserView registerPendingUser(String username, String password, String email, Duration pendingTtl) {
        return toPendingRegistrationView(register(username, password, email, pendingTtl));
    }

    @Transactional
    public User register(String username, String password, String email, Duration pendingTtl) {
        String trimmedUsername = safeTrim(username);
        String trimmedPassword = safeTrim(password);
        String trimmedEmail = safeTrim(email);

        if (!StringUtils.hasText(trimmedUsername) || !StringUtils.hasText(trimmedPassword) || !StringUtils.hasText(trimmedEmail)) {
            throw new BusinessException(INVALID_ARGUMENT, "用户名/密码/邮箱不能为空");
        }

        Date pendingCutoff = pendingUserCutoff(pendingTtl);
        cleanupExpiredPendingConflict(userMapper.selectByName(trimmedUsername), pendingCutoff);
        cleanupExpiredPendingConflict(userMapper.selectByEmail(trimmedEmail), pendingCutoff);

        if (userMapper.selectByName(trimmedUsername) != null) {
            throw new BusinessException(USER_ALREADY_EXISTS);
        }
        if (userMapper.selectByEmail(trimmedEmail) != null) {
            throw new BusinessException(EMAIL_ALREADY_EXISTS);
        }

        User user = new User();
        user.setUsername(trimmedUsername);
        user.setPassword(passwordEncoder.encode(trimmedPassword));
        user.setSalt("");
        user.setEmail(trimmedEmail);
        user.setType(0);
        user.setStatus(0);
        user.setHeaderUrl(String.format("http://images.nowcoder.com/head/%dt.png", new Random().nextInt(1000)));
        user.setCreateTime(new Date());
        user.setId(idGenerator.next());

        try {
            userMapper.insertUser(user);
        } catch (DataIntegrityViolationException ex) {
            if (causedByConstraint(ex, USERNAME_UNIQUE_CONSTRAINT)) {
                throw new BusinessException(USER_ALREADY_EXISTS, ex);
            }
            if (causedByConstraint(ex, EMAIL_UNIQUE_CONSTRAINT)) {
                throw new BusinessException(EMAIL_ALREADY_EXISTS, ex);
            }
            if (userMapper.selectByName(trimmedUsername) != null) {
                throw new BusinessException(USER_ALREADY_EXISTS, ex);
            }
            if (userMapper.selectByEmail(trimmedEmail) != null) {
                throw new BusinessException(EMAIL_ALREADY_EXISTS, ex);
            }
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR, "创建用户失败", ex);
        }
        if (user.getId() == null) {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR, "创建用户失败");
        }
        publishUserPolicyChanged(user.getId());
        return user;
    }

    @Override
    public PendingRegistrationUserView getPendingUser(UUID userId, Duration pendingTtl) {
        return toPendingRegistrationView(getPendingRegistrationUser(userId, pendingTtl));
    }

    public User getPendingRegistrationUser(UUID userId, Duration pendingTtl) {
        if (userId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "userId 非法");
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(USER_NOT_FOUND);
        }

        Date cutoff = pendingUserCutoff(pendingTtl);
        if (isExpiredPendingUser(user, cutoff)) {
            userMapper.deletePendingUserIfExpired(user.getId(), 0, cutoff);
            throw new BusinessException(USER_NOT_FOUND, "注册已过期，请重新注册");
        }
        return user;
    }

    @Override
    @Transactional
    public UserCredentialView activatePendingUser(UUID userId) {
        if (userId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "userId 非法");
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(USER_NOT_FOUND);
        }
        int updated = userMapper.updateStatus(userId, 1);
        if (updated <= 0) {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR, "更新用户状态失败");
        }
        user.setStatus(1);
        publishUserPolicyChanged(userId);
        return toCredentialView(user);
    }

    public void activateUser(UUID userId) {
        activatePendingUser(userId);
    }

    @Override
    public int cleanupExpiredPendingUsers(Duration pendingTtl) {
        return userMapper.deleteExpiredPendingUsers(0, pendingUserCutoff(pendingTtl));
    }

    @Override
    @Transactional
    public void deletePendingUser(UUID userId) {
        if (userId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "userId 非法");
        }
        userMapper.deletePendingUser(userId, 0);
    }

    private void cleanupExpiredPendingConflict(User user, Date cutoff) {
        if (isExpiredPendingUser(user, cutoff)) {
            userMapper.deletePendingUserIfExpired(user.getId(), 0, cutoff);
        }
    }

    private boolean isExpiredPendingUser(User user, Date cutoff) {
        if (user == null || cutoff == null) {
            return false;
        }
        if (user.getStatus() != 0 || user.getCreateTime() == null) {
            return false;
        }
        return !user.getCreateTime().after(cutoff);
    }

    private Date pendingUserCutoff(Duration pendingTtl) {
        Duration ttl = pendingTtl == null || pendingTtl.isZero() || pendingTtl.isNegative()
                ? Duration.ofMinutes(30)
                : pendingTtl;
        return Date.from(Instant.now().minus(ttl));
    }

    private boolean causedByConstraint(Throwable error, String constraintName) {
        String expected = constraintName.toLowerCase(Locale.ROOT);
        for (Throwable current = error; current != null; current = current.getCause()) {
            String message = current.getMessage();
            if (message != null && message.toLowerCase(Locale.ROOT).contains(expected)) {
                return true;
            }
        }
        return false;
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    private PendingRegistrationUserView toPendingRegistrationView(User user) {
        if (user == null || user.getId() == null) {
            return null;
        }
        return new PendingRegistrationUserView(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getStatus(),
                user.getType(),
                user.getHeaderUrl()
        );
    }

    private UserCredentialView toCredentialView(User user) {
        if (user == null || user.getId() == null) {
            return null;
        }
        return new UserCredentialView(
                user.getId(),
                user.getUsername(),
                user.getStatus(),
                user.getType(),
                user.getHeaderUrl()
        );
    }

    private void publishUserPolicyChanged(UUID userId) {
        if (userId == null) {
            return;
        }
        UserPolicyChangedPayload payload = new UserPolicyChangedPayload();
        payload.setUserId(userId);
        userEventPublisher.publishUserPolicyChanged(payload);
    }
}
