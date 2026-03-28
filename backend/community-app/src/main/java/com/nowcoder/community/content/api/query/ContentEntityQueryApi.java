package com.nowcoder.community.content.api.query;

import com.nowcoder.community.content.api.model.ResolvedContentRef;

public interface ContentEntityQueryApi {

    ResolvedContentRef resolve(int entityType, int entityId);
}
