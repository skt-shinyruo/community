package com.nowcoder.community.search.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PostSearchDocument(
        UUID postId,
        UUID userId,
        UUID categoryId,
        List<String> tags,
        String title,
        String content,
        Integer type,
        Integer status,
        long aggregateVersion,
        long scoreVersion,
        Instant createTime,
        Double score
) {

    private static final int DELETED_STATUS = 2;

    public PostSearchDocument {
        tags = tags == null ? List.of() : List.copyOf(tags);
        if (aggregateVersion <= 0L) {
            throw new IllegalArgumentException("post search aggregateVersion must be positive");
        }
        if (scoreVersion < 0L) {
            throw new IllegalArgumentException("post search scoreVersion must not be negative");
        }
    }

    public static PostSearchDocument tombstone(UUID postId, long aggregateVersion) {
        return new PostSearchDocument(
                postId,
                null,
                null,
                List.of(),
                null,
                null,
                null,
                DELETED_STATUS,
                aggregateVersion,
                0L,
                null,
                null
        );
    }
}
