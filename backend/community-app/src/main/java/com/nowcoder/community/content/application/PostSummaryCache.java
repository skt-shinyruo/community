package com.nowcoder.community.content.application;

import com.nowcoder.community.content.application.result.PostSummaryResult;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface PostSummaryCache {

    Map<UUID, PostSummaryResult> getAll(List<UUID> postIds);

    void putAll(List<PostSummaryResult> summaries);

    void evictAll(List<UUID> postIds);

    void terminalEvict(UUID postId);
}
