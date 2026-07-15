package com.nowcoder.community.content.application;

import java.util.UUID;

public interface PostMediaReferenceQueryPort {

    RemoteReferenceStatus findReferenceStatus(UUID objectId, UUID referenceId);

    enum RemoteReferenceStatus {
        ACTIVE,
        RELEASED,
        MISSING,
        UNKNOWN
    }
}
