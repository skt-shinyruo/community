package com.nowcoder.community.im.projection;

import com.nowcoder.community.social.contracts.event.BlockPayload;
import com.nowcoder.community.social.contracts.event.SocialContractEvent;
import com.nowcoder.community.social.contracts.event.SocialEventTypes;
import com.nowcoder.community.user.contracts.event.UserContractEvent;
import com.nowcoder.community.user.contracts.event.UserEventTypes;
import com.nowcoder.community.user.contracts.event.UserPolicyChangedPayload;
import org.junit.jupiter.api.Test;

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
    void socialBlockRelationChangedShouldEnqueueImBlockPolicyUpdate() {
        ImPolicyChangePublisher changePublisher = mock(ImPolicyChangePublisher.class);
        ImPolicyOutboxEnqueuer enqueuer = new ImPolicyOutboxEnqueuer(changePublisher);

        BlockPayload payload = new BlockPayload();
        payload.setBlockerUserId(uuid(1));
        payload.setBlockedUserId(uuid(2));
        payload.setBlocked(Boolean.TRUE);

        enqueuer.onSocialEvent(new SocialContractEvent("evt-social-1", SocialEventTypes.BLOCK_RELATION_CHANGED, payload));

        verify(changePublisher).publishBlockRelationChanged(uuid(1), uuid(2), true);
    }

    @Test
    void userModerationChangedShouldEnqueueImModerationPolicyUpdate() {
        ImPolicyChangePublisher changePublisher = mock(ImPolicyChangePublisher.class);
        ImPolicyOutboxEnqueuer enqueuer = new ImPolicyOutboxEnqueuer(changePublisher);

        UserPolicyChangedPayload payload = new UserPolicyChangedPayload();
        payload.setUserId(uuid(7));

        enqueuer.onUserEvent(new UserContractEvent("evt-user-1", UserEventTypes.USER_POLICY_CHANGED, payload));

        verify(changePublisher).publishUserPolicyChanged(uuid(7));
    }
}
