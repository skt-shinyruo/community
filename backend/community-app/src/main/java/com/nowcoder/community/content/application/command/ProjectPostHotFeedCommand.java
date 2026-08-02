package com.nowcoder.community.content.application.command;

import com.nowcoder.community.content.application.PostProjectionVersionLane;

import java.util.UUID;

public record ProjectPostHotFeedCommand(
        UUID postId,
        UUID boardId,
        String sourceEventId,
        long sourceVersion,
        PostProjectionVersionLane sourceVersionLane,
        boolean terminalDeletion
) {

    /**
     * Compatibility constructor. Event weights are intentionally ignored because projection events only trigger
     * recomputation from owner-domain facts.
     */
    @Deprecated
    public ProjectPostHotFeedCommand(
            UUID postId,
            UUID boardId,
            double ignoredSignalWeight,
            String sourceEventId,
            long sourceVersion,
            PostProjectionVersionLane sourceVersionLane,
            boolean terminalDeletion
    ) {
        this(postId, boardId, sourceEventId, sourceVersion, sourceVersionLane, terminalDeletion);
    }

    /**
     * Compatibility constructor for callers that only ever projected the post lane.
     * New inbound adapters must provide the explicit lane.
     */
    @Deprecated
    public ProjectPostHotFeedCommand(
            UUID postId,
            UUID boardId,
            double ignoredSignalWeight,
            String sourceEventId,
            long sourceVersion,
            boolean terminalDeletion
    ) {
        this(
                postId,
                boardId,
                sourceEventId,
                sourceVersion,
                PostProjectionVersionLane.POST,
                terminalDeletion
        );
    }
}
