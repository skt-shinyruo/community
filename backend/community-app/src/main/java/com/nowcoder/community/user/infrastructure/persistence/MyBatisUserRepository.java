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

    private static final int USER_POLICY_VERSION_COUNTER_ID = 1;
    private static final int USER_SECURITY_VERSION_COUNTER_ID = 1;
    private static final int LEGACY_COMPATIBLE_LOGICAL_BITS = 12;

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
    public void updateRole(UUID userId, int type, long securityVersion) {
        int updated = userMapper.updateType(userId, type, securityVersion);
        if (updated <= 0) {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR, "更新用户角色失败");
        }
    }

    @Override
    public void updateStatus(UUID userId, int status, long securityVersion) {
        int updated = userMapper.updateStatus(userId, status, securityVersion);
        if (updated <= 0) {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR, "更新用户状态失败");
        }
    }

    @Override
    public void updatePassword(UUID userId, String encodedPassword, long securityVersion) {
        int updated = userMapper.updatePassword(userId, encodedPassword, securityVersion);
        if (updated <= 0) {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR, "更新密码失败");
        }
    }

    @Override
    public void updateModerationUntil(UUID userId, Instant muteUntil, Instant banUntil, long policyVersion) {
        int updated = userMapper.updateModerationUntil(
                userId,
                muteUntil == null ? null : Date.from(muteUntil),
                banUntil == null ? null : Date.from(banUntil),
                policyVersion
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
                    toInstant(row.getBanUntil()),
                    row.getPolicyVersion()
            ));
        }
        return statuses;
    }

    @Override
    public long nextUserPolicyVersion(UUID userId) {
        userMapper.upsertPolicyVersionCounter(USER_POLICY_VERSION_COUNTER_ID);
        long current = userMapper.selectPolicyVersionCounterForUpdate(USER_POLICY_VERSION_COUNTER_ID);
        long next = Math.max(current + 1L, legacyCompatibleVersionFloor());
        userMapper.updatePolicyVersionCounter(USER_POLICY_VERSION_COUNTER_ID, next);
        return next;
    }

    @Override
    public long currentUserPolicyVersion() {
        userMapper.upsertPolicyVersionCounter(USER_POLICY_VERSION_COUNTER_ID);
        return userMapper.selectPolicyVersionCounter(USER_POLICY_VERSION_COUNTER_ID);
    }

    @Override
    public long nextUserSecurityVersion(UUID userId) {
        userMapper.upsertSecurityVersionCounter(USER_SECURITY_VERSION_COUNTER_ID);
        long current = userMapper.selectSecurityVersionCounterForUpdate(USER_SECURITY_VERSION_COUNTER_ID);
        long next = Math.max(current + 1L, legacyCompatibleVersionFloor());
        userMapper.updateSecurityVersionCounter(USER_SECURITY_VERSION_COUNTER_ID, next);
        return next;
    }

    @Override
    public long currentUserSecurityVersion() {
        userMapper.upsertSecurityVersionCounter(USER_SECURITY_VERSION_COUNTER_ID);
        return userMapper.selectSecurityVersionCounter(USER_SECURITY_VERSION_COUNTER_ID);
    }

    @Override
    public void insertUser(UserAccount user) {
        int inserted = userMapper.insertUser(toDataObject(user));
        if (inserted <= 0) {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR, "创建用户失败");
        }
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
                toInstant(row.getMuteUntil()),
                toInstant(row.getBanUntil()),
                row.getPolicyVersion(),
                row.getSecurityVersion()
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
        row.setMuteUntil(user.muteUntil() == null ? null : Date.from(user.muteUntil()));
        row.setBanUntil(user.banUntil() == null ? null : Date.from(user.banUntil()));
        row.setPolicyVersion(user.policyVersion());
        row.setSecurityVersion(user.securityVersion());
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
                row.getCreateTime()
        );
    }

    private Instant toInstant(Date value) {
        return value == null ? null : value.toInstant();
    }

    private static long legacyCompatibleVersionFloor() {
        long epochMillis = System.currentTimeMillis();
        return epochMillis <= 0L ? 1L : epochMillis << LEGACY_COMPATIBLE_LOGICAL_BITS;
    }
}
