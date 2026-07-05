package com.nowcoder.community.user.application.command;

public record DispatchUserEventCommand(String eventKey, String payloadJson) {
}
