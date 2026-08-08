package com.nowcoder.community.user.application;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.user.api.action.UserModerationActionApi;
import com.nowcoder.community.user.api.model.UserModerationStateView;
import com.nowcoder.community.user.api.query.UserModerationQueryApi;
import com.nowcoder.community.user.domain.event.UserPolicyEventPublisher;
import com.nowcoder.community.user.domain.model.UserAccount;
import com.nowcoder.community.user.domain.model.UserModerationStatus;
import com.nowcoder.community.user.domain.repository.UserRepository;
import com.nowcoder.community.user.domain.service.UserModerationDomainService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;
import static com.nowcoder.community.user.exception.UserErrorCode.USER_NOT_FOUND;

@Service
public class UserModerationApplicationService implements UserModerationActionApi, UserModerationQueryApi {

    private static final UUID ZERO_UUID = new UUID(0L, 0L);

    private final UserRepository userRepository;
    private final UserModerationDomainService userModerationDomainService;
    private final UserPolicyEventPublisher userPolicyEventPublisher;

    public UserModerationApplicationService(
            UserRepository userRepository,
            UserModerationDomainService userModerationDomainService,
            UserPolicyEventPublisher userPolicyEventPublisher
    ) {
        this.userRepository = userRepository;
        this.userModerationDomainService = userModerationDomainService;
        this.userPolicyEventPublisher = userPolicyEventPublisher;
    }

    @Override
    public UserModerationStateView getModerationState(UUID userId) {
        return toView(getModerationStatus(userId));
    }

    private UserModerationStatus getModerationStatus(UUID userId) {
        if (userId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "userId 非法");
        }
        return userRepository.findById(userId)
                .map(this::toStatus)
                .orElseThrow(() -> new BusinessException(USER_NOT_FOUND));
    }

    @Override
    public List<UserModerationStateView> scanModerationStatesAtVersionAfterId(
            long snapshotVersion,
            UUID afterUserId,
            int limit
    ) {
        if (snapshotVersion < 0L) {
            throw new IllegalArgumentException("snapshotVersion must be non-negative");
        }
        UUID normalizedAfterUserId = afterUserId == null ? ZERO_UUID : afterUserId;
        int normalizedLimit = Math.min(500, Math.max(1, limit));
        return userRepository.scanModerationStatesAtVersionAfterId(
                        snapshotVersion,
                        normalizedAfterUserId,
                        normalizedLimit
                ).stream()
                .filter(status -> status != null && status.userId() != null)
                .map(this::toView)
                .toList();
    }

    @Override
    public long currentModerationProjectionVersion() {
        return userRepository.currentUserPolicyVersion();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void assertActiveModerationActor(UUID actorUserId) {
        requireActiveModerationActorForUpdate(actorUserId, Instant.now());
    }

    private UserModerationStatus applyModerationInternal(UserModerationActionApi.ApplyModerationCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        if (command.actorUserId() == null) {
            throw new BusinessException(INVALID_ARGUMENT, "actorUserId 非法");
        }
        if (command.targetUserId() == null) {
            throw new BusinessException(INVALID_ARGUMENT, "targetUserId 非法");
        }
        String action = userModerationDomainService.requireNonBlankAction(command.action());

        Instant now = Instant.now();
        UserAccount actor = requireActiveModerationActorForUpdate(command.actorUserId(), now);
        UserAccount user = command.targetUserId().equals(command.actorUserId())
                ? actor
                : userRepository.findByIdForUpdate(command.targetUserId())
                        .orElseThrow(() -> new BusinessException(USER_NOT_FOUND));

        userModerationDomainService.requireModerationHierarchy(actor, user);
        boolean wasActivelyBanned = isActiveBan(user.banUntil(), now);
        Instant previousBanUntil = user.banUntil();
        UserModerationStatus next = userModerationDomainService.applyModeration(
                toStatus(user),
                action,
                command.durationSeconds(),
                now
        );
        boolean isActivelyBanned = isActiveBan(next.banUntil(), now);
        boolean activeBanChanged = isActivelyBanned && (!wasActivelyBanned || !sameInstant(previousBanUntil, next.banUntil()));
        long securityVersion = activeBanChanged ? userRepository.nextUserSecurityVersion(command.targetUserId()) : 0L;
        long version = userRepository.nextUserPolicyVersion(command.targetUserId());
        UserModerationStatus versionedNext = new UserModerationStatus(
                next.userId(),
                next.muteUntil(),
                next.banUntil(),
                version
        );
        userRepository.updateModerationUntil(
                command.targetUserId(),
                versionedNext.muteUntil(),
                versionedNext.banUntil(),
                version,
                securityVersion,
                user.policyVersion()
        );
        userPolicyEventPublisher.publishUserPolicyChanged(versionedNext, Instant.now());
        return versionedNext;
    }

    private UserAccount requireActiveModerationActorForUpdate(UUID actorUserId, Instant now) {
        if (actorUserId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "actorUserId 非法");
        }
        userRepository.lockRoleManagement();
        UserAccount actor = userRepository.findByIdForUpdate(actorUserId).orElse(null);
        userModerationDomainService.requireActiveModerationActor(actor, now);
        return actor;
    }

    private UserModerationStatus toStatus(UserAccount user) {
        return new UserModerationStatus(user.id(), user.muteUntil(), user.banUntil(), user.policyVersion());
    }

    private UserModerationStateView toView(UserModerationStatus status) {
        return new UserModerationStateView(status.userId(), status.muteUntil(), status.banUntil(), status.version());
    }

    private boolean isActiveBan(Instant banUntil, Instant now) {
        return banUntil != null && banUntil.isAfter(now);
    }

    private boolean sameInstant(Instant left, Instant right) {
        return left == null ? right == null : left.equals(right);
    }

    @Override
    @Transactional
    public UserModerationStateView applyModeration(UserModerationActionApi.ApplyModerationCommand command) {
        return toView(applyModerationInternal(command));
    }
}
