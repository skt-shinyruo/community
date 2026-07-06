package com.nowcoder.community.notice.application.command;

public record ProjectContentNoticeCommand(String sourceEventId, long sourceVersion, String eventType, Object payload) {
}
