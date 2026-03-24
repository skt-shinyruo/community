package com.nowcoder.community.user.service;

import com.nowcoder.community.auth.exception.AuthErrorCode;
import com.nowcoder.community.common.exception.CommonErrorCode;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.user.entity.User;
import com.nowcoder.community.user.mapper.UserMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;
import static com.nowcoder.community.user.exception.UserErrorCode.EMAIL_ALREADY_EXISTS;
import static com.nowcoder.community.user.exception.UserErrorCode.USER_ALREADY_EXISTS;
import static com.nowcoder.community.user.exception.UserErrorCode.USER_NOT_FOUND;

@Service
public class InternalUserService {
    private static final String USERNAME_UNIQUE_CONSTRAINT = "uk_user_username";
    private static final String EMAIL_UNIQUE_CONSTRAINT = "uk_user_email";

    private final UserMapper userMapper;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public InternalUserService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    public User authenticate(String username, String password) {
        String u = safeTrim(username);
        String p = safeTrim(password);
        if (!StringUtils.hasText(u) || !StringUtils.hasText(p)) {
            throw new BusinessException(AuthErrorCode.INVALID_CREDENTIALS);
        }

        User user = userMapper.selectByName(u);
        if (user == null) {
            throw new BusinessException(AuthErrorCode.INVALID_CREDENTIALS);
        }
        if (user.getStatus() == 0) {
            throw new BusinessException(AuthErrorCode.USER_DISABLED);
        }

        if (!passwordMatches(user, p)) {
            throw new BusinessException(AuthErrorCode.INVALID_CREDENTIALS);
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
            throw new BusinessException(USER_NOT_FOUND);
        }
        return user;
    }

    public void activateUser(int userId) {
        if (userId <= 0) {
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
    }

    public User findByEmailOrNull(String email) {
        String e = safeTrim(email);
        if (!StringUtils.hasText(e)) {
            throw new BusinessException(INVALID_ARGUMENT, "email 不能为空");
        }
        return userMapper.selectByEmail(e);
    }

    @Transactional
    public User register(String username, String password, String email, Duration pendingTtl) {
        String u = safeTrim(username);
        String p = safeTrim(password);
        String emailValue = safeTrim(email);

        if (!StringUtils.hasText(u) || !StringUtils.hasText(p) || !StringUtils.hasText(emailValue)) {
            throw new BusinessException(INVALID_ARGUMENT, "用户名/密码/邮箱不能为空");
        }

        Date pendingCutoff = pendingUserCutoff(pendingTtl);
        cleanupExpiredPendingConflict(userMapper.selectByName(u), pendingCutoff);
        cleanupExpiredPendingConflict(userMapper.selectByEmail(emailValue), pendingCutoff);

        if (userMapper.selectByName(u) != null) {
            throw new BusinessException(USER_ALREADY_EXISTS);
        }
        if (userMapper.selectByEmail(emailValue) != null) {
            throw new BusinessException(EMAIL_ALREADY_EXISTS);
        }

        User user = new User();
        user.setUsername(u);
        user.setPassword(passwordEncoder.encode(p));
        user.setSalt("");
        user.setEmail(emailValue);
        user.setType(0);
        user.setStatus(0);
        user.setHeaderUrl(String.format("http://images.nowcoder.com/head/%dt.png", new Random().nextInt(1000)));
        user.setCreateTime(new Date());

        try {
            userMapper.insertUser(user);
        } catch (DataIntegrityViolationException ex) {
            if (causedByConstraint(ex, USERNAME_UNIQUE_CONSTRAINT)) {
                throw new BusinessException(USER_ALREADY_EXISTS, ex);
            }
            if (causedByConstraint(ex, EMAIL_UNIQUE_CONSTRAINT)) {
                throw new BusinessException(EMAIL_ALREADY_EXISTS, ex);
            }
            if (userMapper.selectByName(u) != null) {
                throw new BusinessException(USER_ALREADY_EXISTS, ex);
            }
            if (userMapper.selectByEmail(emailValue) != null) {
                throw new BusinessException(EMAIL_ALREADY_EXISTS, ex);
            }
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR, "创建用户失败", ex);
        }
        if (user.getId() <= 0) {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR, "创建用户失败");
        }
        return user;
    }

    public User getPendingRegistrationUser(int userId, Duration pendingTtl) {
        if (userId <= 0) {
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

    public int cleanupExpiredPendingUsers(Duration pendingTtl) {
        Date cutoff = pendingUserCutoff(pendingTtl);
        return userMapper.deleteExpiredPendingUsers(0, cutoff);
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
            throw new BusinessException(USER_NOT_FOUND);
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

    public ModerationStatus moderationStatus(int userId) {
        if (userId <= 0) {
            throw new BusinessException(INVALID_ARGUMENT, "userId 非法");
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(USER_NOT_FOUND);
        }

        ModerationStatus s = new ModerationStatus();
        s.setUserId(user.getId());
        s.setMuteUntil(user.getMuteUntil() == null ? null : user.getMuteUntil().toInstant());
        s.setBanUntil(user.getBanUntil() == null ? null : user.getBanUntil().toInstant());
        return s;
    }

    /**
     * internal 投影回填/纠偏：按主键游标向后扫描用户处罚状态（SSOT=user 模块）。
     */
    public List<ModerationStatus> scanModerationStatusesAfterId(int afterId, int limit) {
        int a = Math.max(0, afterId);
        int l = Math.min(500, Math.max(1, limit));
        List<User> users = userMapper.selectModerationUsersAfterId(a, l);
        if (users == null || users.isEmpty()) {
            return List.of();
        }
        List<ModerationStatus> list = new ArrayList<>(users.size());
        for (User u : users) {
            if (u == null || u.getId() <= 0) {
                continue;
            }
            ModerationStatus s = new ModerationStatus();
            s.setUserId(u.getId());
            s.setMuteUntil(u.getMuteUntil() == null ? null : u.getMuteUntil().toInstant());
            s.setBanUntil(u.getBanUntil() == null ? null : u.getBanUntil().toInstant());
            list.add(s);
        }
        return list;
    }

    /**
     * internal 批量用户摘要：用于下游聚合接口避免 N+1 模块调用。
     */
    public List<User> batchGetUserSummaries(List<Integer> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        List<Integer> ids = userIds.stream().filter(id -> id != null && id > 0).distinct().limit(200).toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        List<User> users = userMapper.selectUserSummariesByIds(ids);
        return users == null ? List.of() : users;
    }

    @Transactional
    public ModerationStatus applyModeration(int userId, String action, int durationSeconds) {
        if (userId <= 0) {
            throw new BusinessException(INVALID_ARGUMENT, "userId 非法");
        }
        String act = safeTrim(action).toLowerCase();
        if (!StringUtils.hasText(act)) {
            throw new BusinessException(INVALID_ARGUMENT, "action 不能为空");
        }

        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(USER_NOT_FOUND);
        }

        Instant now = Instant.now();
        Instant muteUntil = user.getMuteUntil() == null ? null : user.getMuteUntil().toInstant();
        Instant banUntil = user.getBanUntil() == null ? null : user.getBanUntil().toInstant();

        int seconds = clampDurationSeconds(durationSeconds);

        if ("mute".equals(act)) {
            muteUntil = seconds <= 0 ? null : now.plusSeconds(seconds);
        } else if ("ban".equals(act)) {
            banUntil = seconds <= 0 ? null : now.plusSeconds(seconds);
        } else if ("unmute".equals(act)) {
            muteUntil = null;
        } else if ("unban".equals(act)) {
            banUntil = null;
        } else {
            throw new BusinessException(INVALID_ARGUMENT, "action 非法");
        }

        int updated = userMapper.updateModerationUntil(
                userId,
                muteUntil == null ? null : Date.from(muteUntil),
                banUntil == null ? null : Date.from(banUntil)
        );
        if (updated <= 0) {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR, "更新处罚状态失败");
        }

        ModerationStatus resp = new ModerationStatus();
        resp.setUserId(userId);
        resp.setMuteUntil(muteUntil);
        resp.setBanUntil(banUntil);

        return resp;
    }

    private int clampDurationSeconds(int seconds) {
        // 防止误传超大值导致溢出或超长期处罚；MVP 先限制在 365 天内。
        int max = 365 * 24 * 3600;
        int s = Math.max(0, seconds);
        return Math.min(max, s);
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

    public static class ModerationStatus {
        private int userId;
        private Instant muteUntil;
        private Instant banUntil;

        public int getUserId() {
            return userId;
        }

        public void setUserId(int userId) {
            this.userId = userId;
        }

        public Instant getMuteUntil() {
            return muteUntil;
        }

        public void setMuteUntil(Instant muteUntil) {
            this.muteUntil = muteUntil;
        }

        public Instant getBanUntil() {
            return banUntil;
        }

        public void setBanUntil(Instant banUntil) {
            this.banUntil = banUntil;
        }
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
            } catch (RuntimeException ignored) {
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

    private String safeTrim(String s) {
        return s == null ? "" : s.trim();
    }
}
