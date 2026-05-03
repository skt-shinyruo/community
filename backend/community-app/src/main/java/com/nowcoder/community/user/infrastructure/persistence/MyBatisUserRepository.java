package com.nowcoder.community.user.infrastructure.persistence;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.exception.CommonErrorCode;
import com.nowcoder.community.user.domain.model.UserAccount;
import com.nowcoder.community.user.domain.model.UserModerationStatus;
import com.nowcoder.community.user.domain.model.UserProfile;
import com.nowcoder.community.user.domain.model.UserSummary;
import com.nowcoder.community.user.domain.repository.UserRepository;
import com.nowcoder.community.user.infrastructure.persistence.dataobject.UserDataObject;
import com.nowcoder.community.user.infrastructure.persistence.mapper.UserMapper;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Repository
public class MyBatisUserRepository implements UserRepository {

    private final UserMapper userMapper;

    public MyBatisUserRepository(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public Optional<UserAccount> findById(UUID userId) {
        return Optional.ofNullable(toAccount(userMapper.selectById(userId)));
    }

    @Override
    public Optional<UserAccount> findByUsername(String username) {
        return Optional.ofNullable(toAccount(userMapper.selectByName(username)));
    }

    @Override
    public Optional<UserAccount> findByEmail(String email) {
        return Optional.ofNullable(toAccount(userMapper.selectByEmail(email)));
    }

    @Override
    public Optional<UserProfile> findProfileById(UUID userId) {
        return Optional.ofNullable(toProfile(userMapper.selectById(userId)));
    }

    @Override
    public List<UserSummary> listSummariesByIds(List<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = userIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .limit(200)
                .toList();
        if (ids.isEmpty()) {
            return List.of();
        }

        List<UserDataObject> rows = userMapper.selectUserSummariesByIds(ids);
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        Map<UUID, UserSummary> byId = new LinkedHashMap<>();
        for (UserDataObject row : rows) {
            UserSummary summary = toSummary(row);
            if (summary != null && summary.id() != null) {
                byId.put(summary.id(), summary);
            }
        }

        List<UserSummary> ordered = new ArrayList<>();
        for (UUID id : ids) {
            UserSummary summary = byId.get(id);
            if (summary != null) {
                ordered.add(summary);
            }
        }
        return ordered;
    }

    @Override
    public void updateHeaderUrl(UUID userId, String headerUrl) {
        int updated = userMapper.updateHeader(userId, headerUrl);
        if (updated <= 0) {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR, "更新头像失败");
        }
    }

    @Override
    public void updateRole(UUID userId, int type) {
        int updated = userMapper.updateType(userId, type);
        if (updated <= 0) {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR, "更新用户角色失败");
        }
    }

    @Override
    public void updateStatus(UUID userId, int status) {
        int updated = userMapper.updateStatus(userId, status);
        if (updated <= 0) {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR, "更新用户状态失败");
        }
    }

    @Override
    public void updatePassword(UUID userId, String encodedPassword) {
        int updated = userMapper.updatePassword(userId, encodedPassword);
        if (updated <= 0) {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR, "更新密码失败");
        }
    }

    @Override
    public void updateModerationUntil(UUID userId, Instant muteUntil, Instant banUntil) {
        int updated = userMapper.updateModerationUntil(
                userId,
                muteUntil == null ? null : Date.from(muteUntil),
                banUntil == null ? null : Date.from(banUntil)
        );
        if (updated <= 0) {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR, "更新处罚状态失败");
        }
    }

    @Override
    public List<UserModerationStatus> scanModerationStatesAfterId(UUID afterUserId, int limit) {
        UUID normalizedAfterId = afterUserId == null ? new UUID(0L, 0L) : afterUserId;
        int normalizedLimit = Math.min(500, Math.max(1, limit));
        List<UserDataObject> rows = userMapper.selectModerationUsersAfterId(normalizedAfterId, normalizedLimit);
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        List<UserModerationStatus> statuses = new ArrayList<>(rows.size());
        for (UserDataObject row : rows) {
            if (row == null || row.getId() == null) {
                continue;
            }
            statuses.add(new UserModerationStatus(
                    row.getId(),
                    toInstant(row.getMuteUntil()),
                    toInstant(row.getBanUntil())
            ));
        }
        return statuses;
    }

    @Override
    public void insertUser(UserAccount user) {
        int inserted = userMapper.insertUser(toDataObject(user));
        if (inserted <= 0) {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR, "创建用户失败");
        }
    }

    @Override
    public void insertPendingUser(UserAccount user) {
        insertUser(user);
    }

    @Override
    public int deletePendingUserIfExpired(UUID userId, int status, Instant cutoff) {
        return userMapper.deletePendingUserIfExpired(
                userId,
                status,
                cutoff == null ? null : Date.from(cutoff)
        );
    }

    @Override
    public int deletePendingUser(UUID userId, int status) {
        return userMapper.deletePendingUser(userId, status);
    }

    @Override
    public List<UUID> listExpiredPendingUserIds(int status, Instant cutoff, int limit) {
        List<UUID> ids = userMapper.selectExpiredPendingUserIds(
                status,
                cutoff == null ? null : Date.from(cutoff),
                limit
        );
        return ids == null ? List.of() : ids;
    }

    private UserAccount toAccount(UserDataObject row) {
        if (row == null || row.getId() == null) {
            return null;
        }
        return new UserAccount(
                row.getId(),
                row.getUsername(),
                row.getPassword(),
                row.getSalt(),
                row.getEmail(),
                row.getType(),
                row.getStatus(),
                row.getHeaderUrl(),
                row.getCreateTime(),
                row.getScore(),
                toInstant(row.getMuteUntil()),
                toInstant(row.getBanUntil())
        );
    }

    private UserDataObject toDataObject(UserAccount user) {
        UserDataObject row = new UserDataObject();
        row.setId(user.id());
        row.setUsername(user.username());
        row.setPassword(user.encodedPassword());
        row.setSalt(user.salt());
        row.setEmail(user.email());
        row.setType(user.type());
        row.setStatus(user.status());
        row.setHeaderUrl(user.headerUrl());
        row.setCreateTime(user.createTime());
        row.setScore(user.score());
        row.setMuteUntil(user.muteUntil() == null ? null : Date.from(user.muteUntil()));
        row.setBanUntil(user.banUntil() == null ? null : Date.from(user.banUntil()));
        return row;
    }

    private UserSummary toSummary(UserDataObject row) {
        if (row == null || row.getId() == null) {
            return null;
        }
        return new UserSummary(row.getId(), row.getUsername(), row.getHeaderUrl(), row.getType());
    }

    private UserProfile toProfile(UserDataObject row) {
        if (row == null || row.getId() == null) {
            return null;
        }
        return new UserProfile(
                row.getId(),
                row.getUsername(),
                row.getHeaderUrl(),
                row.getType(),
                row.getStatus(),
                row.getCreateTime(),
                row.getScore()
        );
    }

    private Instant toInstant(Date value) {
        return value == null ? null : value.toInstant();
    }
}
