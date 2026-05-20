package com.nowcoder.community.user.application;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.user.application.command.ApplyUserModerationCommand;
import com.nowcoder.community.user.domain.event.UserPolicyEventPublisher;
import com.nowcoder.community.user.domain.model.UserAccount;
import com.nowcoder.community.user.domain.model.UserModerationStatus;
import com.nowcoder.community.user.domain.repository.UserRepository;
import com.nowcoder.community.user.domain.service.UserModerationDomainService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;
import static com.nowcoder.community.user.exception.UserErrorCode.USER_NOT_FOUND;

@Service
public class UserModerationApplicationService {

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

    public UserModerationStatus getModerationState(UUID userId) {
        if (userId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "userId 非法");
        }
        return userRepository.findById(userId)
                .map(this::toStatus)
                .orElseThrow(() -> new BusinessException(USER_NOT_FOUND));
    }

    public List<UserModerationStatus> scanModerationStatesAfterId(UUID afterUserId, int limit) {
        UUID normalizedAfterUserId = afterUserId == null ? ZERO_UUID : afterUserId;
        int normalizedLimit = Math.min(500, Math.max(1, limit));
        return userRepository.scanModerationStatesAfterId(normalizedAfterUserId, normalizedLimit).stream()
                .filter(status -> status != null && status.userId() != null)
                .toList();
    }

    public long currentModerationProjectionVersion() {
        return userRepository.currentUserPolicyVersion();
    }

    @Transactional
    public UserModerationStatus applyModeration(ApplyUserModerationCommand command) {
        if (command == null) {
            throw new BusinessException(INVALID_ARGUMENT, "request 不能为空");
        }
        UUID userId = command.userId();
        if (userId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "userId 非法");
        }
        String action = userModerationDomainService.requireNonBlankAction(command.action());
        UserAccount user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(USER_NOT_FOUND));

        UserModerationStatus next = userModerationDomainService.applyModeration(
                toStatus(user),
                action,
                command.durationSeconds(),
                Instant.now()
        );
        long version = userRepository.nextUserPolicyVersion(userId);
        UserModerationStatus versionedNext = new UserModerationStatus(
                next.userId(),
                next.muteUntil(),
                next.banUntil(),
                version
        );
        userRepository.updateModerationUntil(userId, versionedNext.muteUntil(), versionedNext.banUntil(), version);
        userPolicyEventPublisher.publishUserPolicyChanged(versionedNext, Instant.now());
        return versionedNext;
    }

    private UserModerationStatus toStatus(UserAccount user) {
        return new UserModerationStatus(user.id(), user.muteUntil(), user.banUntil(), user.policyVersion());
    }
}
