package com.nowcoder.community.notice.application.command;

import java.util.List;
import java.util.UUID;

public record MarkNoticeReadCommand(UUID userId, List<UUID> ids) {
}
