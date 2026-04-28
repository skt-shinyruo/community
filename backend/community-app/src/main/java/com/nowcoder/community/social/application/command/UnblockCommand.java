package com.nowcoder.community.social.application.command;

import java.util.UUID;

public record UnblockCommand(UUID actorUserId, UUID targetUserId) {
}
