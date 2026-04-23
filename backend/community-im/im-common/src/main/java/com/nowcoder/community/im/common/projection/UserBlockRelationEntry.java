package com.nowcoder.community.im.common.projection;

import java.util.UUID;

public record UserBlockRelationEntry(
        UUID blockerUserId,
        UUID blockedUserId,
        boolean active
) {
}
