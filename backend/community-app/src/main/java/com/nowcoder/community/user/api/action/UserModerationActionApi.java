package com.nowcoder.community.user.api.action;

import com.nowcoder.community.user.api.model.UserModerationStateView;

import java.util.UUID;

public interface UserModerationActionApi {

    UserModerationStateView applyModeration(UUID userId, String action, int durationSeconds);
}
