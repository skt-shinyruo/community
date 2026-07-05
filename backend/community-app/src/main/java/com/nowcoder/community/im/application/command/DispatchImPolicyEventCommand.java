package com.nowcoder.community.im.application.command;

public record DispatchImPolicyEventCommand(
        String outboxEventId,
        String outboxKey,
        String payloadJson
) {
}
