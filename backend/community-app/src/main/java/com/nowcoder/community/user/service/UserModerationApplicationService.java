package com.nowcoder.community.user.service;

import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class UserModerationApplicationService {

    private final UserModerationService userModerationService;

    public UserModerationApplicationService(UserModerationService userModerationService) {
        this.userModerationService = userModerationService;
    }

    public UserModerationService.ModerationStatus applyModeration(UUID userId, String action, int durationSeconds) {
        return userModerationService.applyModeration(userId, action, durationSeconds);
    }
}
