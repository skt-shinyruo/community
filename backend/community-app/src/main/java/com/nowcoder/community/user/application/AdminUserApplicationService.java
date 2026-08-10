package com.nowcoder.community.user.application;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.user.application.port.UserAuditLogPort;
import com.nowcoder.community.user.domain.model.UserAccount;
import com.nowcoder.community.user.domain.repository.UserRepository;
import com.nowcoder.community.user.domain.service.UserRoleDomainService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;

@Service
public class AdminUserApplicationService {

    private final UserRepository userRepository;
    private final UserRoleDomainService userRoleDomainService;
    private final UserAuditLogPort userAuditLogPort;
    private final Clock clock;

    public AdminUserApplicationService(
            UserRepository userRepository,
            UserRoleDomainService userRoleDomainService,
            UserAuditLogPort userAuditLogPort,
            Clock clock
    ) {
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository must not be null");
        this.userRoleDomainService = Objects.requireNonNull(userRoleDomainService, "userRoleDomainService must not be null");
        this.userAuditLogPort = Objects.requireNonNull(userAuditLogPort, "userAuditLogPort must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public AdminUserResult search(UUID userId, String username, String email) {
        Optional<UserAccount> user = resolveSearchTarget(userId, username, email);
        return user.map(this::toResult).orElse(null);
    }

    @Transactional
    public void updateRole(UpdateRoleCommand command) {
        Objects.requireNonNull(command, "command must not be null");

        String reason = userRoleDomainService.requireValidCommand(
                true,
                command.targetUserId(),
                command.type(),
                command.reason(),
                command.confirm()
        );
        userRepository.lockRoleManagement();
        Instant now = Instant.now(clock);
        UserAccount actor = userRepository.findByIdForUpdate(command.actorUserId()).orElse(null);
        userRoleDomainService.requireActiveAdmin(actor, now);
        UserAccount target = command.targetUserId().equals(command.actorUserId())
                ? actor
                : userRepository.findByIdForUpdate(command.targetUserId()).orElse(null);
        userRoleDomainService.requireRoleUpdateAllowed(
                command.actorUserId(),
                command.targetUserId(),
                command.type(),
                actor,
                target,
                now
        );

        int fromType = target.type();
        int toType = command.type();
        if (fromType == toType) {
            return;
        }

        long securityVersion = userRepository.nextUserSecurityVersion(command.targetUserId());
        userRepository.updateRole(command.targetUserId(), toType, securityVersion);
        userAuditLogPort.recordRoleUpdated(command.actorUserId(), command.targetUserId(), fromType, toType, reason);
    }

    private Optional<UserAccount> resolveSearchTarget(UUID userId, String username, String email) {
        if (userId != null) {
            return userRepository.findById(userId);
        }
        if (StringUtils.hasText(username)) {
            return userRepository.findByUsername(username.trim());
        }
        if (StringUtils.hasText(email)) {
            return userRepository.findByEmail(email.trim());
        }
        throw new BusinessException(INVALID_ARGUMENT, "请提供 userId/username/email 之一");
    }

    private AdminUserResult toResult(UserAccount user) {
        if (user == null || user.id() == null) {
            return null;
        }
        return new AdminUserResult(
                user.id(),
                user.username(),
                user.email(),
                user.type(),
                user.status(),
                user.headerUrl(),
                user.createTime()
        );
    }

    public record UpdateRoleCommand(
            UUID actorUserId,
            UUID targetUserId,
            int type,
            String reason,
            boolean confirm
    ) {
    }

    public record AdminUserResult(
            UUID id,
            String username,
            String email,
            int type,
            int status,
            String headerUrl,
            Date createTime
    ) {
    }
}
