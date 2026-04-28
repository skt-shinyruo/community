package com.nowcoder.community.content.application.port;

import com.nowcoder.community.content.domain.model.ModerationTarget;

import java.util.UUID;

public interface ContentModerationPort {

    void applyContentAction(UUID actorId, ModerationTarget target, String action, String reason);
}
