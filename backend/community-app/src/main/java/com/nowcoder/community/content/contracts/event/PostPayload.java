package com.nowcoder.community.content.contracts.event;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PostPayload(
        UUID postId,
        UUID userId,
        UUID categoryId,
        List<String> tags,
        String title,
        String content,
        int type,
        int status,
        Instant createTime,
        Instant updateTime,
        Double score,
        @JsonInclude(JsonInclude.Include.NON_DEFAULT) long scoreVersion,
        @JsonInclude(JsonInclude.Include.NON_DEFAULT) long aggregateVersion
) implements Serializable {
}
