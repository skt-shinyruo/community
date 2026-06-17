package com.nowcoder.community.notice.application.command;

public record ProjectSocialNoticeCommand(String sourceEventId, String eventType, Object payload) {
}
