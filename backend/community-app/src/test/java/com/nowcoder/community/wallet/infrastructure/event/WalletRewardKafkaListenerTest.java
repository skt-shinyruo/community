package com.nowcoder.community.wallet.infrastructure.event;

import com.nowcoder.community.common.json.JacksonJsonCodec;
import com.nowcoder.community.common.json.JsonCodec;
import com.nowcoder.community.common.json.JsonMappers;
import com.nowcoder.community.content.contracts.event.ContentContractEvent;
import com.nowcoder.community.content.contracts.event.ContentContractEventCodec;
import com.nowcoder.community.content.contracts.event.ContentEventTypes;
import com.nowcoder.community.content.contracts.event.PostPayload;
import com.nowcoder.community.content.infrastructure.event.JacksonContentContractEventCodec;
import com.nowcoder.community.social.contracts.event.LikePayload;
import com.nowcoder.community.social.contracts.event.SocialContractEvent;
import com.nowcoder.community.social.contracts.event.SocialContractEventCodec;
import com.nowcoder.community.social.contracts.event.SocialEventTypes;
import com.nowcoder.community.social.infrastructure.event.JacksonSocialContractEventCodec;
import com.nowcoder.community.wallet.application.WalletRewardApplicationService;
import com.nowcoder.community.wallet.application.WalletRewardProjectionApplicationService;
import com.nowcoder.community.wallet.application.command.WalletRewardCommand;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.annotation.KafkaListener;

import java.time.Instant;
import java.util.UUID;

import static com.nowcoder.community.common.constants.EntityTypes.POST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class WalletRewardKafkaListenerTest {

    private final JsonCodec jsonCodec = new JacksonJsonCodec(JsonMappers.standard());
    private final ContentContractEventCodec contentContractEventCodec =
            new JacksonContentContractEventCodec(jsonCodec);
    private final SocialContractEventCodec socialContractEventCodec =
            new JacksonSocialContractEventCodec(jsonCodec);

    @Test
    void shouldPreserveTopicsAndConsumerProperties() throws Exception {
        KafkaListener content = WalletRewardKafkaListener.class
                .getDeclaredMethod("onContentEvent", ContentContractEvent.class)
                .getAnnotation(KafkaListener.class);
        KafkaListener social = WalletRewardKafkaListener.class
                .getDeclaredMethod("onSocialEvent", SocialContractEvent.class)
                .getAnnotation(KafkaListener.class);

        assertThat(content.topics()).containsExactly("${content.events.kafka-topic:content.events}");
        assertThat(social.topics()).containsExactly("${social.events.kafka-topic:social.events}");
        assertThat(content.groupId()).isEqualTo("${user.reward.kafka.consumer.group-id:user-reward-projection}");
        assertThat(social.groupId()).isEqualTo(content.groupId());
        assertThat(content.concurrency()).isEqualTo("${user.reward.kafka.consumer.concurrency:3}");
    }

    @Test
    void postPublishedShouldMapToStableWalletRequestId() {
        WalletRewardApplicationService walletReward = mock(WalletRewardApplicationService.class);
        WalletRewardKafkaListener listener = listener(walletReward);
        PostPayload payload = new PostPayload();
        payload.setPostId(uuid(100));
        payload.setUserId(uuid(7));

        listener.onContentEvent(new ContentContractEvent(
                "ce:post:published:1", null, null, ContentEventTypes.POST_PUBLISHED,
                Instant.EPOCH, 1L, jsonCodec.valueToTree(payload)
        ));

        verify(walletReward).applyDelta(new WalletRewardCommand(
                "wallet-reward:post-published:" + uuid(100), uuid(7), 10, "PostPublished"
        ));
    }

    @Test
    void replayedLikeWithDifferentEnvelopeIdsShouldUseSameBusinessKey() {
        WalletRewardApplicationService walletReward = mock(WalletRewardApplicationService.class);
        WalletRewardKafkaListener listener = listener(walletReward);
        LikePayload payload = likePayload(uuid(1), uuid(100), uuid(2));

        listener.onSocialEvent(event("se:like:created:1", SocialEventTypes.LIKE_CREATED, payload));
        listener.onSocialEvent(event("se:like:created:2", SocialEventTypes.LIKE_CREATED, payload));

        verify(walletReward, times(2)).applyDelta(new WalletRewardCommand(
                "wallet-reward:" + payload.getRelationKey() + ":created", uuid(2), 1, "LikeCreated"
        ));
    }

    @Test
    void lifecycleIdentityShouldTakePriorityOverLegacyRelationKey() {
        WalletRewardApplicationService walletReward = mock(WalletRewardApplicationService.class);
        WalletRewardKafkaListener listener = listener(walletReward);
        LikePayload payload = likePayload(uuid(1), uuid(100), uuid(2));
        payload.setRelationInstanceId(uuid(501));

        listener.onSocialEvent(event("se:like:created:new", SocialEventTypes.LIKE_CREATED, payload));
        listener.onSocialEvent(event("se:like:removed:new", SocialEventTypes.LIKE_REMOVED, payload));

        verify(walletReward).applyDelta(new WalletRewardCommand(
                "wallet-reward:" + uuid(501) + ":created", uuid(2), 1, "LikeCreated"
        ));
        verify(walletReward).applyDelta(new WalletRewardCommand(
                "wallet-reward:" + uuid(501) + ":removed", uuid(2), -1, "LikeRemoved"
        ));
    }

    @Test
    void replayAndOutOfOrderDeliveryShouldRemainScopedToEachLifecycleInstance() {
        WalletRewardApplicationService walletReward = mock(WalletRewardApplicationService.class);
        WalletRewardKafkaListener listener = listener(walletReward);
        LikePayload first = likePayload(uuid(1), uuid(100), uuid(2));
        first.setRelationInstanceId(uuid(511));
        LikePayload second = likePayload(uuid(1), uuid(100), uuid(2));
        second.setRelationInstanceId(uuid(512));

        listener.onSocialEvent(event("remove-first", SocialEventTypes.LIKE_REMOVED, first));
        listener.onSocialEvent(event("create-first", SocialEventTypes.LIKE_CREATED, first));
        listener.onSocialEvent(event("create-first-replay", SocialEventTypes.LIKE_CREATED, first));
        listener.onSocialEvent(event("create-second", SocialEventTypes.LIKE_CREATED, second));
        listener.onSocialEvent(event("remove-second", SocialEventTypes.LIKE_REMOVED, second));

        ArgumentCaptor<WalletRewardCommand> commandCaptor = ArgumentCaptor.forClass(WalletRewardCommand.class);
        verify(walletReward, times(5)).applyDelta(commandCaptor.capture());
        assertThat(commandCaptor.getAllValues()).containsExactly(
                new WalletRewardCommand("wallet-reward:" + uuid(511) + ":removed", uuid(2), -1, "LikeRemoved"),
                new WalletRewardCommand("wallet-reward:" + uuid(511) + ":created", uuid(2), 1, "LikeCreated"),
                new WalletRewardCommand("wallet-reward:" + uuid(511) + ":created", uuid(2), 1, "LikeCreated"),
                new WalletRewardCommand("wallet-reward:" + uuid(512) + ":created", uuid(2), 1, "LikeCreated"),
                new WalletRewardCommand("wallet-reward:" + uuid(512) + ":removed", uuid(2), -1, "LikeRemoved")
        );
    }

    @Test
    void likeRemovedShouldReverseRewardAndSelfLikeShouldBeIgnored() {
        WalletRewardApplicationService walletReward = mock(WalletRewardApplicationService.class);
        WalletRewardKafkaListener listener = listener(walletReward);
        LikePayload payload = likePayload(uuid(1), uuid(100), uuid(2));

        listener.onSocialEvent(event("se:like:removed:1", SocialEventTypes.LIKE_REMOVED, payload));
        listener.onSocialEvent(event(
                "se:like:created:self", SocialEventTypes.LIKE_CREATED,
                likePayload(uuid(1), uuid(100), uuid(1))
        ));

        verify(walletReward).applyDelta(new WalletRewardCommand(
                "wallet-reward:" + payload.getRelationKey() + ":removed", uuid(2), -1, "LikeRemoved"
        ));
    }

    @Test
    void recognizedMalformedEventShouldThrowWhileUnknownEventIsIgnored() {
        WalletRewardApplicationService walletReward = mock(WalletRewardApplicationService.class);
        WalletRewardKafkaListener listener = listener(walletReward);
        PostPayload malformed = new PostPayload();
        malformed.setPostId(uuid(100));

        assertThatThrownBy(() -> listener.onContentEvent(new ContentContractEvent(
                "ce:post:published:missing-user", null, null, ContentEventTypes.POST_PUBLISHED,
                Instant.EPOCH, 1L, jsonCodec.valueToTree(malformed)
        ))).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ce:post:published:missing-user");
        listener.onContentEvent(new ContentContractEvent(
                "ce:post:updated", null, null, ContentEventTypes.POST_UPDATED,
                Instant.EPOCH, 1L, jsonCodec.valueToTree(new PostPayload())
        ));

        verifyNoInteractions(walletReward);
    }

    private WalletRewardKafkaListener listener(WalletRewardApplicationService walletReward) {
        return new WalletRewardKafkaListener(
                contentContractEventCodec,
                socialContractEventCodec,
                new WalletRewardProjectionApplicationService(walletReward)
        );
    }

    private SocialContractEvent event(String eventId, String type, LikePayload payload) {
        return new SocialContractEvent(
                eventId, null, null, type, Instant.EPOCH, 1L, jsonCodec.valueToTree(payload));
    }

    private static LikePayload likePayload(UUID actor, UUID entityId, UUID owner) {
        LikePayload payload = new LikePayload();
        payload.setActorUserId(actor);
        payload.setEntityType(POST);
        payload.setEntityId(entityId);
        payload.setEntityUserId(owner);
        payload.setRelationKey("like:" + actor + ":" + POST + ":" + entityId);
        return payload;
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }
}
