package com.nowcoder.community.growth.application.command;

public record DispatchTaskProgressEventCommand(
        TaskProgressDispatchKind kind,
        String eventKey,
        String payloadJson
) {
}
