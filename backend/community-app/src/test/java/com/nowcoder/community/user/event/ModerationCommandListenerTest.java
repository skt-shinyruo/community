package com.nowcoder.community.user.event;

import com.nowcoder.community.content.contracts.event.ContentContractEvent;
import com.nowcoder.community.content.contracts.event.ContentEventTypes;
import com.nowcoder.community.content.contracts.event.ModerationCommandPayload;
import com.nowcoder.community.user.service.UserModerationService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ModerationCommandListenerTest {

    @Test
    void listenerShouldOnlyExposeUserModerationServiceConstructor() {
        assertThat(ModerationCommandListener.class.getDeclaredConstructors())
                .singleElement()
                .satisfies(constructor -> assertThat(constructor.getParameterTypes()).containsExactly(
                        UserModerationService.class
                ));
    }

    @Test
    void moderationCommandShouldApplyModerationLocally() {
        UserModerationService userModerationService = mock(UserModerationService.class);
        ModerationCommandListener listener = new ModerationCommandListener(userModerationService);

        ModerationCommandPayload payload = new ModerationCommandPayload();
        payload.setUserId(5);
        payload.setAction("mute");
        payload.setDurationSeconds(60);

        listener.onContentEvent(new ContentContractEvent("moderation-evt-1", ContentEventTypes.MODERATION_COMMAND_REQUESTED, payload));

        verify(userModerationService).applyModeration(5, "mute", 60);
    }
}
