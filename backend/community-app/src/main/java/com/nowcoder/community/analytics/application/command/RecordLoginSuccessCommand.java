package com.nowcoder.community.analytics.application.command;

import java.util.UUID;

public record RecordLoginSuccessCommand(UUID userId, boolean recordDau) {
}
