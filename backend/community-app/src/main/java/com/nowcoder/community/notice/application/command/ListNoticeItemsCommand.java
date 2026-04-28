package com.nowcoder.community.notice.application.command;

import java.util.UUID;

public record ListNoticeItemsCommand(UUID userId, String topic, Integer page, Integer size) {
}
