package com.nowcoder.community.im.infrastructure.event;

import com.nowcoder.community.social.contracts.event.BlockPayload;
import com.nowcoder.community.social.contracts.event.SocialContractEvent;
import com.nowcoder.community.social.contracts.event.SocialEventTypes;
import com.nowcoder.community.user.contracts.event.UserContractEvent;
import com.nowcoder.community.user.contracts.event.UserEventTypes;
import com.nowcoder.community.user.contracts.event.UserPolicyChangedPayload;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Arrays;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ImPolicyOutboxEnqueuerTest {

    @Test
    void outboxEnqueuerShouldOnlyExposeImPolicyChangePublisherConstructor() {
        assertThat(ImPolicyOutboxEnqueuer.class.getDeclaredConstructors())
                .singleElement()
                .satisfies(constructor -> assertThat(constructor.getParameterTypes()).containsExactly(
                        ImPolicyChangePublisher.class
                ));
    }

    @Test
    void outboxEnqueuerShouldNotHandleSocialBlockEventsForCorrectness() {
        assertThat(Arrays.stream(ImPolicyOutboxEnqueuer.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(TransactionalEventListener.class))
                .map(method -> method.getParameterTypes()[0]))
                .containsExactly(UserContractEvent.class);
    }

    @Test
    void userModerationChangedShouldEnqueueImModerationPolicyUpdate() {
        ImPolicyChangePublisher changePublisher = mock(ImPolicyChangePublisher.class);
        ImPolicyOutboxEnqueuer enqueuer = new ImPolicyOutboxEnqueuer(changePublisher);

        UserPolicyChangedPayload payload = new UserPolicyChangedPayload();
        payload.setUserId(uuid(7));
        payload.setUserExists(true);
        payload.setCanSendPrivate(true);
        payload.setOccurredAtEpochMillis(1712345678901L);
        payload.setVersion(777L);

        enqueuer.onUserEvent(new UserContractEvent("evt-user-1", UserEventTypes.USER_POLICY_CHANGED, payload));

        ArgumentCaptor<UserPolicyChangedPayload> payloadCaptor = ArgumentCaptor.forClass(UserPolicyChangedPayload.class);
        verify(changePublisher).publishUserPolicyChanged(payloadCaptor.capture());
        assertThat(payloadCaptor.getValue().getUserId()).isEqualTo(uuid(7));
        assertThat(payloadCaptor.getValue().isUserExists()).isTrue();
        assertThat(payloadCaptor.getValue().getVersion()).isEqualTo(777L);
    }
}
