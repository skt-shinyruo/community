package com.nowcoder.community.notice.controller.dto;

import java.util.Date;
import java.util.UUID;

public record NoticeItemResponse(
        UUID id,
        UUID senderUserId,
        UUID recipientUserId,
        String topic,
        String content,
        int status,
        Date createTime
) {
}
