package com.nowcoder.community.notice.application.command;

public record ProjectSocialNoticeCommand(String sourceEventId, long sourceVersion, String eventType, Object payload) {
}
