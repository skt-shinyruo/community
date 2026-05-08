package com.nowcoder.community.oss.application.command;

import java.util.UUID;

public record DeleteObjectCommand(
        UUID objectId,
        String actorId
) {
}
