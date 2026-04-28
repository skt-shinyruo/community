package com.nowcoder.community.im.application;

import com.nowcoder.community.im.common.projection.UserBlockRelationSnapshot;
import com.nowcoder.community.im.common.projection.UserMessagingPolicySnapshot;
import com.nowcoder.community.im.projection.ImPolicySnapshotService;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class ImPolicySnapshotApplicationService {

    private final ImPolicySnapshotService snapshotService;

    public ImPolicySnapshotApplicationService(ImPolicySnapshotService snapshotService) {
        this.snapshotService = snapshotService;
    }

    public UserMessagingPolicySnapshot userPolicies(UUID afterUserId, int limit) {
        return snapshotService.userPolicies(afterUserId, limit);
    }

    public UserBlockRelationSnapshot blockRelations(UUID afterBlockerUserId, UUID afterBlockedUserId, int limit) {
        return snapshotService.blockRelations(afterBlockerUserId, afterBlockedUserId, limit);
    }
}
