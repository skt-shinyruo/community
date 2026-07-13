package com.nowcoder.community.user.domain.repository;

import com.nowcoder.community.user.domain.model.UserAccount;
import com.nowcoder.community.user.domain.model.UserModerationStatus;
import com.nowcoder.community.user.domain.model.UserProfile;
import com.nowcoder.community.user.domain.model.UserSummary;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository {

    Optional<UserAccount> findById(UUID userId);

    Optional<UserAccount> findByUsername(String username);

    Optional<UserAccount> findByEmail(String email);

    Optional<UserProfile> findProfileById(UUID userId);

    List<UserSummary> listSummariesByIds(List<UUID> userIds);

    void updateHeaderUrl(UUID userId, String headerUrl);

    void updateRole(UUID userId, int type, long securityVersion);

    void updateRole(UUID userId, int type);

    void updateStatus(UUID userId, int status, long securityVersion);

    void updateStatus(UUID userId, int status);

    void updatePassword(UUID userId, String encodedPassword, long securityVersion);

    void updatePassword(UUID userId, String encodedPassword);

    void updateModerationUntil(UUID userId, Instant muteUntil, Instant banUntil, long policyVersion, long securityVersion);

    List<UserModerationStatus> scanModerationStatesAfterId(UUID afterUserId, int limit);

    long nextUserPolicyVersion(UUID userId);

    long currentUserPolicyVersion();

    long nextUserSecurityVersion(UUID userId);

    long currentUserSecurityVersion();

    void insertUser(UserAccount user);
}
