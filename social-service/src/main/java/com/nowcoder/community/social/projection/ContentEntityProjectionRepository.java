package com.nowcoder.community.social.projection;

import org.springframework.stereotype.Service;

import java.util.Date;

@Service
public class ContentEntityProjectionRepository {

    private final ContentEntityProjectionMapper mapper;

    public ContentEntityProjectionRepository(ContentEntityProjectionMapper mapper) {
        this.mapper = mapper;
    }

    public ContentEntityProjection find(int entityType, long entityId) {
        if (entityType <= 0 || entityId <= 0) {
            return null;
        }
        return mapper.find(entityType, entityId);
    }

    public void upsertIfNewer(int entityType, long entityId, long entityUserId, long postId, int status, Date updatedAt) {
        if (entityType <= 0 || entityId <= 0) {
            return;
        }
        Date ts = updatedAt == null ? new Date() : updatedAt;
        long eu = Math.max(0L, entityUserId);
        long pid = Math.max(0L, postId);
        mapper.upsertIfNewer(entityType, entityId, eu, pid, status, ts);
    }
}

