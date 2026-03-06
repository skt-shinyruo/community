package com.nowcoder.community.content.event;

public record ContentLocalEvent(String eventId, String type, Object payload) {
}
