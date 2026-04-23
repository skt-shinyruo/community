package com.nowcoder.community.im.common.projection;

import java.util.UUID;

public record UserMessagingPolicyEntry(
        UUID userId,
        boolean userExists,
        boolean suspended,
        boolean muted,
        boolean allowPrivateMessages
) {
}
