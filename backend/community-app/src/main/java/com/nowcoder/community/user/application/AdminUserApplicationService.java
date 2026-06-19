package com.nowcoder.community.user.application;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.user.application.command.UpdateUserRoleCommand;
import com.nowcoder.community.user.application.port.UserAuditLogPort;
import com.nowcoder.community.user.application.result.AdminUserResult;
import com.nowcoder.community.user.domain.model.UserAccount;
import com.nowcoder.community.user.domain.repository.RefreshTokenSessionRepository;
import com.nowcoder.community.user.domain.repository.UserRepository;
import com.nowcoder.community.user.domain.service.UserRoleDomainService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Optional;
import java.util.UUID;

import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;

@Service
public class AdminUserApplicationService {

    private final UserRepository userRepository;
    private final UserRoleDomainService userRoleDomainService;
    private final UserAuditLogPort userAuditLogPort;
    private final RefreshTokenSessionRepository refreshTokenSessionRepository;

    public AdminUserApplicationService(
            UserRepository userRepository,
            UserRoleDomainService userRoleDomainService,
            UserAuditLogPort userAuditLogPort,
            RefreshTokenSessionRepository refreshTokenSessionRepository
    ) {
        this.userRepository = userRepository;
        this.userRoleDomainService = userRoleDomainService;
        this.userAuditLogPort = userAuditLogPort;
        this.refreshTokenSessionRepository = refreshTokenSessionRepository;
    }

    public AdminUserResult search(UUID userId, String username, String email) {
        Optional<UserAccount> user = resolveSearchTarget(userId, username, email);
        return user.map(this::toResult).orElse(null);
    }

    @Transactional
    public void updateRole(UpdateUserRoleCommand command) {
        if (command == null) {
            userRoleDomainService.requireValidCommand(false, null, 0, null, false);
            return;
        }

        String reason = userRoleDomainService.requireValidCommand(
                true,
                command.targetUserId(),
                command.type(),
                command.reason(),
                command.confirm()
        );
        UserAccount target = userRepository.findById(command.targetUserId()).orElse(null);
        userRoleDomainService.requireRoleUpdateAllowed(command.actorUserId(), command.targetUserId(), command.type(), target);

        int fromType = target.type();
        int toType = command.type();
        if (fromType == toType) {
            return;
        }

        long securityVersion = userRepository.nextUserSecurityVersion(command.targetUserId());
        userRepository.updateRole(command.targetUserId(), toType, securityVersion);
        refreshTokenSessionRepository.revokeByUserId(command.targetUserId());
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
}
