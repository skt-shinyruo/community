package com.nowcoder.community.notice.application.result;

import java.util.Date;
import java.util.UUID;

public record NoticeItemResult(
        UUID id,
        UUID senderUserId,
        UUID recipientUserId,
        String noticeTopic,
        String content,
        int status,
        Date createTime
) {
}
