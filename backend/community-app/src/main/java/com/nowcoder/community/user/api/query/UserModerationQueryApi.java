package com.nowcoder.community.user.api.query;

import com.nowcoder.community.user.api.model.UserModerationStateView;

import java.util.List;
import java.util.UUID;

public interface UserModerationQueryApi {

    UserModerationStateView getModerationState(UUID userId);

    List<UserModerationStateView> scanModerationStatesAtVersionAfterId(
            long snapshotVersion,
            UUID afterUserId,
            int limit
    );

    long currentModerationProjectionVersion();
}
