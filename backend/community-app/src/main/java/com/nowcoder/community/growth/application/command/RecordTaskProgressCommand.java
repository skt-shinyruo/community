package com.nowcoder.community.growth.application.command;

import java.time.LocalDate;
import java.util.UUID;

public record RecordTaskProgressCommand(
        UUID userId,
        String triggerEventType,
        String sourceEventId,
        LocalDate bizDate
) {
}
