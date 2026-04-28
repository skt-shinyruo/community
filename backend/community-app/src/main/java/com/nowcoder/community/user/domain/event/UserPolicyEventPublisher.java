package com.nowcoder.community.user.domain.event;

import com.nowcoder.community.user.domain.model.UserModerationStatus;

import java.time.Instant;
import java.util.UUID;

public interface UserPolicyEventPublisher {

    void publishUserPolicyChanged(UserModerationStatus status, Instant occurredAt);

    void publishUserPolicyChanged(UUID userId, boolean userExists, Instant occurredAt);
}
