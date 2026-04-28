package com.nowcoder.community.notice.application.command;

import java.util.UUID;

public record CreateNoticeCommand(UUID toUserId, String topic, String contentJson) {
}
