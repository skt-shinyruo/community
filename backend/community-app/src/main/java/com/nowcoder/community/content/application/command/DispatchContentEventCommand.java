package com.nowcoder.community.content.application.command;

public record DispatchContentEventCommand(String eventKey, String payloadJson) {
}
