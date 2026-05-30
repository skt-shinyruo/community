package com.nowcoder.community.notice.domain.model;

import java.util.UUID;

public record NoticeProjection(
        UUID toUserId,
        String topic,
        String sourceEventId,
        String sourceEventType,
        Object payload
) {
}
