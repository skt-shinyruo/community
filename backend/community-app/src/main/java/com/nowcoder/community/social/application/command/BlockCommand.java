package com.nowcoder.community.social.application.command;

import java.util.UUID;

public record BlockCommand(UUID actorUserId, UUID targetUserId) {
}
