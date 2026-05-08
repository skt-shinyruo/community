package com.nowcoder.community.oss.application.command;

import java.util.Objects;
import java.util.UUID;

public record CompleteObjectUploadCommand(
        UUID sessionId,
        UUID objectId,
        UUID versionId,
        ObjectUploadContent content
) {

    public CompleteObjectUploadCommand {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(objectId, "objectId");
        Objects.requireNonNull(versionId, "versionId");
        Objects.requireNonNull(content, "content");
    }
}
