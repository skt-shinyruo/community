package com.nowcoder.community.oss.application.command;

import java.util.UUID;

public record ReleaseObjectReferenceCommand(
        UUID objectId,
        UUID referenceId,
        String actorId
) {
}
