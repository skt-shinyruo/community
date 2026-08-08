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

    enum InsertResult {
        CREATED,
        ALREADY_EXISTS,
        CONFLICT
    }

    Optional<UserAccount> findById(UUID userId);

    Optional<UserAccount> findByIdForUpdate(UUID userId);

    Optional<UserAccount> findByUsername(String username);

    Optional<UserAccount> findByEmail(String email);

    Optional<UserProfile> findProfileById(UUID userId);

    List<UserSummary> listSummariesByIds(List<UUID> userIds);

    void updateHeaderUrl(UUID userId, String headerUrl);

    void updateRole(UUID userId, int type, long securityVersion);

    /**
     * Establishes the security-counter-before-user-row lock order for registration,
     * role changes, and moderation decisions. Role-authorized decisions also use
     * this shared lock so each actor sees the preceding committed decision.
     */
    void lockRoleManagement();

    void updateStatus(UUID userId, int status, long securityVersion);

    void updatePassword(UUID userId, String encodedPassword, long securityVersion);

    boolean updatePasswordIfSecurityVersion(
            UUID userId,
            String encodedPassword,
            long securityVersion,
            long expectedSecurityVersion
    );

    void updateModerationUntil(
            UUID userId,
            Instant muteUntil,
            Instant banUntil,
            long policyVersion,
            long securityVersion,
            long expectedPolicyVersion
    );

    List<UserModerationStatus> scanModerationStatesAtVersionAfterId(
            long snapshotVersion,
            UUID afterUserId,
            int limit
    );

    long nextUserPolicyVersion(UUID userId);

    long currentUserPolicyVersion();

    /**
     * Allocates above both the durable counter and this user's persisted epoch.
     * Security epochs are compared only within the same user, so allocation does
     * not require an unindexed maximum scan across all users.
     */
    long nextUserSecurityVersion(UUID userId);

    /**
     * Returns the durable allocator watermark. Authentication freshness is
     * evaluated against the security version persisted on the relevant user.
     */
    long currentUserSecurityVersion();

    InsertResult insertUser(UserAccount user);
}
