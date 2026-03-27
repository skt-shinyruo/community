package com.nowcoder.community.user.service;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.exception.CommonErrorCode;
import com.nowcoder.community.user.entity.User;
import com.nowcoder.community.user.mapper.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;
import static com.nowcoder.community.user.exception.UserErrorCode.USER_NOT_FOUND;

@Service
public class UserModerationService {

    private final UserMapper userMapper;

    public UserModerationService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    public ModerationStatus moderationStatus(int userId) {
        if (userId <= 0) {
            throw new BusinessException(INVALID_ARGUMENT, "userId 非法");
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(USER_NOT_FOUND);
        }
        return toStatus(user);
    }

    public List<ModerationStatus> scanModerationStatusesAfterId(int afterId, int limit) {
        int normalizedAfterId = Math.max(0, afterId);
        int normalizedLimit = Math.min(500, Math.max(1, limit));
        List<User> users = userMapper.selectModerationUsersAfterId(normalizedAfterId, normalizedLimit);
        if (users == null || users.isEmpty()) {
            return List.of();
        }
        List<ModerationStatus> statuses = new ArrayList<>(users.size());
        for (User user : users) {
            if (user == null || user.getId() <= 0) {
                continue;
            }
            statuses.add(toStatus(user));
        }
        return statuses;
    }

    @Transactional
    public ModerationStatus applyModeration(int userId, String action, int durationSeconds) {
        if (userId <= 0) {
            throw new BusinessException(INVALID_ARGUMENT, "userId 非法");
        }
        String value = safeTrim(action).toLowerCase();
        if (!StringUtils.hasText(value)) {
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

        if ("mute".equals(value)) {
            muteUntil = seconds <= 0 ? null : now.plusSeconds(seconds);
        } else if ("ban".equals(value)) {
            banUntil = seconds <= 0 ? null : now.plusSeconds(seconds);
        } else if ("unmute".equals(value)) {
            muteUntil = null;
        } else if ("unban".equals(value)) {
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

        ModerationStatus status = new ModerationStatus();
        status.setUserId(userId);
        status.setMuteUntil(muteUntil);
        status.setBanUntil(banUntil);
        return status;
    }

    private ModerationStatus toStatus(User user) {
        ModerationStatus status = new ModerationStatus();
        status.setUserId(user.getId());
        status.setMuteUntil(user.getMuteUntil() == null ? null : user.getMuteUntil().toInstant());
        status.setBanUntil(user.getBanUntil() == null ? null : user.getBanUntil().toInstant());
        return status;
    }

    private int clampDurationSeconds(int seconds) {
        int max = 365 * 24 * 3600;
        int normalized = Math.max(0, seconds);
        return Math.min(max, normalized);
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
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
}
