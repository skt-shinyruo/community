package com.nowcoder.community.oss.application.command;

import java.util.UUID;

public record RevokeObjectAccessCommand(
        UUID objectId,
        UUID grantId,
        String actorId
) {
}
