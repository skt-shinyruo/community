package com.nowcoder.community.user.service;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.exception.CommonErrorCode;
import com.nowcoder.community.user.api.model.UserModerationStateView;
import com.nowcoder.community.user.api.query.UserModerationQueryApi;
import com.nowcoder.community.user.contracts.event.UserModerationChangedPayload;
import com.nowcoder.community.user.entity.User;
import com.nowcoder.community.user.event.UserEventPublisher;
import com.nowcoder.community.user.mapper.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;
import static com.nowcoder.community.user.exception.UserErrorCode.USER_NOT_FOUND;

@Service
public class UserModerationService implements UserModerationQueryApi {

    private final UserMapper userMapper;
    private final UserEventPublisher userEventPublisher;

    public UserModerationService(UserMapper userMapper, UserEventPublisher userEventPublisher) {
        this.userMapper = userMapper;
        this.userEventPublisher = userEventPublisher;
    }

    @Override
    public UserModerationStateView getModerationState(UUID userId) {
        return toView(moderationStatus(userId));
    }

    @Override
    public List<UserModerationStateView> scanModerationStatesAfterId(UUID afterUserId, int limit) {
        return scanModerationStatusesAfterId(afterUserId, limit).stream()
                .map(this::toView)
                .toList();
    }

    public ModerationStatus moderationStatus(UUID userId) {
        if (userId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "userId 非法");
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(USER_NOT_FOUND);
        }
        return toStatus(user);
    }

    public List<ModerationStatus> scanModerationStatusesAfterId(UUID afterId, int limit) {
        UUID normalizedAfterId = afterId == null ? new UUID(0L, 0L) : afterId;
        int normalizedLimit = Math.min(500, Math.max(1, limit));
        List<User> users = userMapper.selectModerationUsersAfterId(normalizedAfterId, normalizedLimit);
        if (users == null || users.isEmpty()) {
            return List.of();
        }
        List<ModerationStatus> statuses = new ArrayList<>(users.size());
        for (User user : users) {
            if (user == null || user.getId() == null) {
                continue;
            }
            statuses.add(toStatus(user));
        }
        return statuses;
    }

    @Transactional
    public ModerationStatus applyModeration(UUID userId, String action, int durationSeconds) {
        if (userId == null) {
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
        UserModerationChangedPayload payload = new UserModerationChangedPayload();
        payload.setUserId(userId);
        userEventPublisher.publishUserModerationChanged(payload);
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

    private UserModerationStateView toView(ModerationStatus status) {
        if (status == null) {
            return null;
        }
        return new UserModerationStateView(status.getUserId(), status.getMuteUntil(), status.getBanUntil());
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    public static class ModerationStatus {
        private UUID userId;
        private Instant muteUntil;
        private Instant banUntil;

        public UUID getUserId() {
            return userId;
        }

        public void setUserId(UUID userId) {
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
