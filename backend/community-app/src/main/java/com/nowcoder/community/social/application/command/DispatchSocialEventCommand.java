package com.nowcoder.community.social.application.command;

public record DispatchSocialEventCommand(String eventKey, String payloadJson) {
}
