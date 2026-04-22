package com.nowcoder.community.content.api.query;

import com.nowcoder.community.content.api.model.ResolvedContentRef;

import java.util.UUID;

public interface ContentEntityQueryApi {

    ResolvedContentRef resolve(int entityType, UUID entityId);
}
