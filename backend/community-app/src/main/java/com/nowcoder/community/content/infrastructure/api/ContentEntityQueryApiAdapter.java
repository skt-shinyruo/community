package com.nowcoder.community.content.infrastructure.api;

import com.nowcoder.community.content.api.model.ResolvedContentRef;
import com.nowcoder.community.content.api.query.ContentEntityQueryApi;
import com.nowcoder.community.content.application.ContentEntityResolutionApplicationService;
import com.nowcoder.community.content.application.result.ResolvedContentResult;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class ContentEntityQueryApiAdapter implements ContentEntityQueryApi {

    private final ContentEntityResolutionApplicationService applicationService;

    public ContentEntityQueryApiAdapter(ContentEntityResolutionApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @Override
    public ResolvedContentRef resolve(int entityType, UUID entityId) {
        ResolvedContentResult result = applicationService.resolve(entityType, entityId);
        return new ResolvedContentRef(result.entityUserId(), result.postId());
    }
}
