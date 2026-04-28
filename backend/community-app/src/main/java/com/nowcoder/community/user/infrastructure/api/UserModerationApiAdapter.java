package com.nowcoder.community.user.infrastructure.api;

import com.nowcoder.community.user.api.action.UserModerationActionApi;
import com.nowcoder.community.user.api.model.UserModerationStateView;
import com.nowcoder.community.user.api.query.UserModerationQueryApi;
import com.nowcoder.community.user.application.UserModerationApplicationService;
import com.nowcoder.community.user.application.command.ApplyUserModerationCommand;
import com.nowcoder.community.user.domain.model.UserModerationStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class UserModerationApiAdapter implements UserModerationActionApi, UserModerationQueryApi {

    private final UserModerationApplicationService applicationService;

    public UserModerationApiAdapter(UserModerationApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @Override
    public UserModerationStateView applyModeration(UUID userId, String action, int durationSeconds) {
        return toView(applicationService.applyModeration(
                new ApplyUserModerationCommand(userId, action, durationSeconds)
        ));
    }

    @Override
    public UserModerationStateView getModerationState(UUID userId) {
        return toView(applicationService.getModerationState(userId));
    }

    @Override
    public List<UserModerationStateView> scanModerationStatesAfterId(UUID afterUserId, int limit) {
        return applicationService.scanModerationStatesAfterId(afterUserId, limit).stream()
                .map(this::toView)
                .toList();
    }

    private UserModerationStateView toView(UserModerationStatus status) {
        if (status == null) {
            return null;
        }
        return new UserModerationStateView(status.userId(), status.muteUntil(), status.banUntil());
    }
}
