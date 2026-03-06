package com.nowcoder.community.user.event;

import com.nowcoder.community.content.api.event.ContentEventTypes;
import com.nowcoder.community.content.api.event.payload.ModerationCommandPayload;
import com.nowcoder.community.content.event.ContentLocalEvent;
import com.nowcoder.community.user.service.InternalUserService;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ModerationCommandListenerTest {

    @Test
    void moderationCommandShouldApplyModerationLocally() {
        InternalUserService internalUserService = mock(InternalUserService.class);
        ModerationCommandListener listener = new ModerationCommandListener(internalUserService);

        ModerationCommandPayload payload = new ModerationCommandPayload();
        payload.setUserId(5);
        payload.setAction("mute");
        payload.setDurationSeconds(60);

        listener.onContentEvent(new ContentLocalEvent("moderation-evt-1", ContentEventTypes.MODERATION_COMMAND_REQUESTED, payload));

        verify(internalUserService).applyModeration(5, "mute", 60);
    }
}
