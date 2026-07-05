package com.nowcoder.community.notice.application.command;

import java.util.UUID;

public record ListNoticeItemsCommand(UUID userId, String noticeTopic, Integer page, Integer size) {
}
