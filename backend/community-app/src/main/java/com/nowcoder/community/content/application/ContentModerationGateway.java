package com.nowcoder.community.content.application;

import com.nowcoder.community.content.domain.model.ModerationTarget;

import java.util.UUID;

public interface ContentModerationGateway {

    void applyContentAction(UUID actorId, ModerationTarget target, String action, String reason);
}
