package com.nowcoder.community.notice.application.command;

public record ProjectContentNoticeCommand(String sourceEventId, String eventType, Object payload) {
}
