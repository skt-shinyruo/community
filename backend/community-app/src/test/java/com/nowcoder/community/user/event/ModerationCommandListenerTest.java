package com.nowcoder.community.user.event;

import com.nowcoder.community.content.contracts.event.ContentContractEvent;
import com.nowcoder.community.content.contracts.event.ContentEventTypes;
import com.nowcoder.community.content.contracts.event.ModerationCommandPayload;
import com.nowcoder.community.user.service.UserModerationApplicationService;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ModerationCommandListenerTest {

    @Test
    void listenerShouldOnlyExposeUserModerationApplicationServiceConstructor() {
        assertThat(ModerationCommandListener.class.getDeclaredConstructors())
                .singleElement()
                .satisfies(constructor -> assertThat(constructor.getParameterTypes()).containsExactly(
                        UserModerationApplicationService.class
                ));
    }

    @Test
    void moderationCommandShouldApplyModerationLocally() {
        UserModerationApplicationService userModerationApplicationService = mock(UserModerationApplicationService.class);
        ModerationCommandListener listener = new ModerationCommandListener(userModerationApplicationService);
        UUID userId = UUID.fromString("00000000-0000-7000-8000-000000000005");

        ModerationCommandPayload payload = new ModerationCommandPayload();
        payload.setUserId(userId);
        payload.setAction("mute");
        payload.setDurationSeconds(60);

        listener.onContentEvent(new ContentContractEvent("moderation-evt-1", ContentEventTypes.MODERATION_COMMAND_REQUESTED, payload));

        verify(userModerationApplicationService).applyModeration(userId, "mute", 60);
    }
}
