package com.nowcoder.community.oss.domain.service;

import com.nowcoder.community.oss.domain.model.OssAccessGrant;
import com.nowcoder.community.oss.domain.model.OssObject;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class OssObjectAccessPolicy {

    private static final String USER_OWNER_TYPE = "USER";
    private static final String READ_PERMISSION = "READ";

    public boolean canRead(
            OssObject object,
            UUID requestedVersionId,
            String actorId,
            List<OssAccessGrant> grants,
            Instant now
    ) {
        Objects.requireNonNull(object, "object");
        Objects.requireNonNull(now, "now");

        String normalizedActorId = normalizeActorId(actorId);
        if (normalizedActorId == null) {
            return false;
        }
        if (isOwner(object, normalizedActorId)) {
            return true;
        }
        if (grants == null || grants.isEmpty()) {
            return false;
        }

        return grants.stream().anyMatch(grant -> permitsRead(
                grant, object.objectId(), requestedVersionId, normalizedActorId, now));
    }

    public boolean canManage(OssObject object, String actorId) {
        Objects.requireNonNull(object, "object");
        String normalizedActorId = normalizeActorId(actorId);
        return normalizedActorId != null && isOwner(object, normalizedActorId);
    }

    private boolean isOwner(OssObject object, String actorId) {
        return USER_OWNER_TYPE.equals(object.ownerType()) && actorId.equals(object.ownerId());
    }

    private boolean permitsRead(
            OssAccessGrant grant,
            UUID objectId,
            UUID requestedVersionId,
            String actorId,
            Instant now
    ) {
        return grant != null
                && objectId.equals(grant.objectId())
                && versionMatches(grant.versionId(), requestedVersionId)
                && USER_OWNER_TYPE.equals(grant.principalType())
                && actorId.equals(grant.principalValue())
                && READ_PERMISSION.equals(grant.permission())
                && grant.activeAt(now);
    }

    private boolean versionMatches(UUID grantVersionId, UUID requestedVersionId) {
        return grantVersionId == null || grantVersionId.equals(requestedVersionId);
    }

    private String normalizeActorId(String actorId) {
        if (actorId == null || actorId.isBlank()) {
            return null;
        }
        return actorId.trim();
    }
}
