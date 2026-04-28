package com.nowcoder.community.user.application.port;

import java.util.UUID;

public interface UserAuditLogPort {

    void recordRoleUpdated(UUID actorUserId, UUID targetUserId, int fromType, int toType, String reason);
}
