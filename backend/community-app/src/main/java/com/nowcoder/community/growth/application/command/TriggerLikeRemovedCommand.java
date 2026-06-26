package com.nowcoder.community.growth.application.command;

import java.util.UUID;

public record TriggerLikeRemovedCommand(String relationKey, UUID entityUserId) {
}
