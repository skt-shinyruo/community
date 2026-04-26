package com.nowcoder.community.user.service;

import com.nowcoder.community.user.api.action.UserModerationActionApi;
import com.nowcoder.community.user.api.model.UserModerationStateView;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class UserModerationApplicationService implements UserModerationActionApi {

    private final UserModerationService userModerationService;

    public UserModerationApplicationService(UserModerationService userModerationService) {
        this.userModerationService = userModerationService;
    }

    @Override
    public UserModerationStateView applyModeration(UUID userId, String action, int durationSeconds) {
        UserModerationService.ModerationStatus status = userModerationService.applyModeration(userId, action, durationSeconds);
        return new UserModerationStateView(status.getUserId(), status.getMuteUntil(), status.getBanUntil());
    }
}
