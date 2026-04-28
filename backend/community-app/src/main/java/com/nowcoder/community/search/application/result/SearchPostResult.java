package com.nowcoder.community.search.application.result;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SearchPostResult(
        UUID postId,
        UUID userId,
        UUID categoryId,
        List<String> tags,
        String title,
        String highlightedTitle,
        String highlightedContent,
        Instant createTime,
        Double score
) {

    public SearchPostResult {
        tags = tags == null ? List.of() : List.copyOf(tags);
    }
}
