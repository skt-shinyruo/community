package com.nowcoder.community.user.api.model;

import java.time.Instant;

public record UserModerationStateView(int userId, Instant muteUntil, Instant banUntil) {
}
