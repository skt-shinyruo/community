package com.nowcoder.community.content.application.command;

import org.springframework.util.StringUtils;

public record UpdateHotFeedDegradationSignalCommand(
        boolean degraded,
        String reason
) {

    public UpdateHotFeedDegradationSignalCommand normalized() {
        return new UpdateHotFeedDegradationSignalCommand(degraded, StringUtils.hasText(reason) ? reason.trim() : "");
    }
}
