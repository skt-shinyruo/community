package com.nowcoder.community.social.api.action;

import java.util.UUID;

/**
 * Synchronous owner boundary for writes that must serialize with block changes.
 */
public interface SocialInteractionActionApi {

    void assertInteractionAllowed(UUID actorUserId, UUID targetUserId);
}
