package com.nowcoder.community.content.service;

import com.nowcoder.community.content.api.model.ResolvedContentRef;
import com.nowcoder.community.content.api.query.ContentEntityQueryApi;
import org.springframework.stereotype.Service;

@Service
public class ContentEntityService {

    private final ContentEntityQueryApi contentEntityQueryApi;

    public ContentEntityService(ContentEntityQueryApi contentEntityQueryApi) {
        this.contentEntityQueryApi = contentEntityQueryApi;
    }

    public ResolvedEntity resolve(int entityType, int entityId) {
        ResolvedContentRef resolved = contentEntityQueryApi.resolve(entityType, entityId);
        return new ResolvedEntity(resolved.entityUserId(), resolved.postId());
    }

    public record ResolvedEntity(int entityUserId, int postId) {
    }
}
