package com.nowcoder.community.user.infrastructure.audit;

import com.nowcoder.community.user.application.port.UserAuditLogPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class Slf4jUserAuditLogAdapter implements UserAuditLogPort {

    private static final Logger log = LoggerFactory.getLogger(Slf4jUserAuditLogAdapter.class);

    @Override
    public void recordRoleUpdated(UUID actorUserId, UUID targetUserId, int fromType, int toType, String reason) {
        log.info("[audit] action=admin_user_role_update actorUserId={} targetUserId={} fromType={} toType={} reason={}",
                actorUserId, targetUserId, fromType, toType, reason);
    }
}
