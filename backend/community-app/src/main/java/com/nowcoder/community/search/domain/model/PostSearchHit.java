package com.nowcoder.community.search.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PostSearchHit(
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

    public PostSearchHit {
        tags = tags == null ? List.of() : List.copyOf(tags);
    }
}
