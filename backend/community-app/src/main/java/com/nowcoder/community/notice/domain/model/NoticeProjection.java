package com.nowcoder.community.notice.domain.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.UUID;

public record NoticeProjection(
        UUID toUserId,
        String topic,
        String sourceEventId,
        String sourceEventType,
        JsonNode payload
) {
}
