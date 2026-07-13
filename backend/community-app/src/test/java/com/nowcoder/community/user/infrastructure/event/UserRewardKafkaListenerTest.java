package com.nowcoder.community.user.infrastructure.event;

import com.nowcoder.community.common.json.JacksonJsonCodec;
import com.nowcoder.community.common.json.JsonCodec;
import com.nowcoder.community.common.json.JsonMappers;
import com.nowcoder.community.content.contracts.event.CommentPayload;
import com.nowcoder.community.content.contracts.event.ContentContractEvent;
import com.nowcoder.community.content.contracts.event.ContentEventTypes;
import com.nowcoder.community.content.contracts.event.PostPayload;
import com.nowcoder.community.social.contracts.event.LikePayload;
import com.nowcoder.community.social.contracts.event.SocialContractEvent;
import com.nowcoder.community.social.contracts.event.SocialEventTypes;
import com.nowcoder.community.user.application.UserRewardApplicationService;
import com.nowcoder.community.wallet.api.action.WalletRewardActionApi;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static com.nowcoder.community.common.constants.EntityTypes.POST;
import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class UserRewardKafkaListenerTest {

    private final JsonCodec jsonCodec = new JacksonJsonCodec(JsonMappers.standard());

    @Test
    void postPublishedShouldApplyAuthorRewardThroughApplicationService() {
        WalletRewardActionApi walletRewardActionApi = mock(WalletRewardActionApi.class);
        UserRewardKafkaListener listener = listener(walletRewardActionApi);
        PostPayload payload = new PostPayload();
        payload.setPostId(uuid(100));
        payload.setUserId(uuid(7));

        listener.onContentEvent(new ContentContractEvent("ce:post:published:1", null, null, ContentEventTypes.POST_PUBLISHED, java.time.Instant.EPOCH, 1L, payload));

        verify(walletRewardActionApi).applyDelta(
                "wallet-reward:post-published:" + uuid(100),
                uuid(7),
                10,
                "PostPublished"
        );
    }

    @Test
    void commentCreatedShouldApplyAuthorRewardThroughApplicationService() {
        WalletRewardActionApi walletRewardActionApi = mock(WalletRewardActionApi.class);
        UserRewardKafkaListener listener = listener(walletRewardActionApi);
        CommentPayload payload = new CommentPayload();
        payload.setCommentId(uuid(200));
        payload.setUserId(uuid(3));

        listener.onContentEvent(new ContentContractEvent("ce:comment:created:1", null, null, ContentEventTypes.COMMENT_CREATED, java.time.Instant.EPOCH, 1L, payload));

        verify(walletRewardActionApi).applyDelta(
                "wallet-reward:comment-created:" + uuid(200),
                uuid(3),
                2,
                "CommentCreated"
        );
    }

    @Test
    void mapLikeContentPayloadShouldConvertBeforeApplyingReward() {
        WalletRewardActionApi walletRewardActionApi = mock(WalletRewardActionApi.class);
        UserRewardKafkaListener listener = listener(walletRewardActionApi);

        listener.onContentEvent(new ContentContractEvent("ce:post:published:map", null, null, ContentEventTypes.POST_PUBLISHED, java.time.Instant.EPOCH, 1L, Map.of("postId", uuid(101).toString(), "userId", uuid(8).toString())));

        verify(walletRewardActionApi).applyDelta(
                "wallet-reward:post-published:" + uuid(101),
                uuid(8),
                10,
                "PostPublished"
        );
    }

    @Test
    void likeCreatedWithDifferentKafkaEventIdsShouldUseSamePayloadBusinessRewardSource() {
        WalletRewardActionApi walletRewardActionApi = mock(WalletRewardActionApi.class);
        UserRewardKafkaListener listener = listener(walletRewardActionApi);
        LikePayload payload = likePayload(uuid(1), uuid(100), uuid(2));

        listener.onSocialEvent(new SocialContractEvent("se:like:created:01965429-b34a-7000-8000-000000000041", null, null, SocialEventTypes.LIKE_CREATED, java.time.Instant.EPOCH, 1L, payload));
        listener.onSocialEvent(new SocialContractEvent("se:like:created:01965429-b34a-7000-8000-000000000042", null, null, SocialEventTypes.LIKE_CREATED, java.time.Instant.EPOCH, 1L, payload));

        verify(walletRewardActionApi, org.mockito.Mockito.times(2)).applyDelta(
                "wallet-reward:" + payload.getRelationKey() + ":created",
                uuid(2),
                1,
                "LikeCreated"
        );
    }

    @Test
    void likeRewardBusinessSourceShouldFitWalletRequestIdLimit() {
        LikePayload payload = likePayload(uuid(1), uuid(100), uuid(2));
        String requestId = "wallet-reward:" + payload.getRelationKey() + ":created";

        org.assertj.core.api.Assertions.assertThat(requestId)
                .hasSizeGreaterThan(96)
                .hasSizeLessThanOrEqualTo(128);
    }

    @Test
    void likeRemovedShouldUsePayloadBusinessRewardSource() {
        WalletRewardActionApi walletRewardActionApi = mock(WalletRewardActionApi.class);
        UserRewardKafkaListener listener = listener(walletRewardActionApi);
        LikePayload payload = likePayload(uuid(1), uuid(100), uuid(2));

        listener.onSocialEvent(new SocialContractEvent("se:like:removed:01965429-b34a-7000-8000-000000000042", null, null, SocialEventTypes.LIKE_REMOVED, java.time.Instant.EPOCH, 1L, payload));

        verify(walletRewardActionApi).applyDelta(
                "wallet-reward:" + payload.getRelationKey() + ":removed",
                uuid(2),
                -1,
                "LikeRemoved"
        );
    }

    @Test
    void mapLikeSocialPayloadShouldConvertBeforeApplyingReward() {
        WalletRewardActionApi walletRewardActionApi = mock(WalletRewardActionApi.class);
        UserRewardKafkaListener listener = listener(walletRewardActionApi);
        listener.onSocialEvent(new SocialContractEvent("se:like:created:01965429-b34a-7000-8000-000000000043", null, null, SocialEventTypes.LIKE_CREATED, java.time.Instant.EPOCH, 1L, Map.of(
                        "actorUserId", uuid(1).toString(),
                        "entityType", POST,
                        "entityId", uuid(100).toString(),
                        "entityUserId", uuid(2).toString(),
                        "relationKey", "like:" + uuid(1) + ":" + POST + ":" + uuid(100)
                )));

        verify(walletRewardActionApi).applyDelta(
                "wallet-reward:like:" + uuid(1) + ":" + POST + ":" + uuid(100) + ":created",
                uuid(2),
                1,
                "LikeCreated"
        );
    }

    @Test
    void likeCreatedWithoutRelationKeyShouldFailDelivery() {
        WalletRewardActionApi walletRewardActionApi = mock(WalletRewardActionApi.class);
        UserRewardKafkaListener listener = listener(walletRewardActionApi);
        LikePayload payload = likePayload(uuid(1), uuid(100), uuid(2));
        payload.setRelationKey(null);

        assertThatThrownBy(() -> listener.onSocialEvent(new SocialContractEvent(
                "se:like:created:missing-relation-key", null, null, SocialEventTypes.LIKE_CREATED,
                Instant.EPOCH, 1L, payload)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("se:like:created:missing-relation-key");

        verifyNoInteractions(walletRewardActionApi);
    }

    @Test
    void recognizedSocialEventWithMissingEntityTypeShouldFailDelivery() {
        WalletRewardActionApi walletRewardActionApi = mock(WalletRewardActionApi.class);
        UserRewardKafkaListener listener = listener(walletRewardActionApi);

        assertThatThrownBy(() -> listener.onSocialEvent(new SocialContractEvent("se:like:created:missing-entity-type", null, null, SocialEventTypes.LIKE_CREATED, java.time.Instant.EPOCH, 1L, Map.of(
                        "actorUserId", uuid(1).toString(),
                        "entityId", uuid(100).toString(),
                        "entityUserId", uuid(2).toString(),
                        "relationKey", "like:" + uuid(1) + ":" + POST + ":" + uuid(100)
                ))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(SocialEventTypes.LIKE_CREATED)
                .hasMessageContaining("se:like:created:missing-entity-type");

        verifyNoInteractions(walletRewardActionApi);
    }

    @Test
    void selfLikeShouldBeIgnored() {
        WalletRewardActionApi walletRewardActionApi = mock(WalletRewardActionApi.class);
        UserRewardKafkaListener listener = listener(walletRewardActionApi);

        listener.onSocialEvent(new SocialContractEvent("se:like:created:self", null, null, SocialEventTypes.LIKE_CREATED, java.time.Instant.EPOCH, 1L, likePayload(uuid(1), uuid(100), uuid(1))));

        verifyNoInteractions(walletRewardActionApi);
    }

    @Test
    void unsupportedEventsShouldBeIgnored() {
        WalletRewardActionApi walletRewardActionApi = mock(WalletRewardActionApi.class);
        UserRewardKafkaListener listener = listener(walletRewardActionApi);

        listener.onContentEvent(new ContentContractEvent("ce:post:updated", null, null, ContentEventTypes.POST_UPDATED, java.time.Instant.EPOCH, 1L, new PostPayload()));
        listener.onSocialEvent(new SocialContractEvent("se:follow:created", null, null, SocialEventTypes.FOLLOW_CREATED, java.time.Instant.EPOCH, 1L, new Object()));

        verifyNoInteractions(walletRewardActionApi);
    }

    @Test
    void recognizedContentEventWithMissingIdentityShouldFailDelivery() {
        WalletRewardActionApi walletRewardActionApi = mock(WalletRewardActionApi.class);
        UserRewardKafkaListener listener = listener(walletRewardActionApi);
        PostPayload payload = new PostPayload();
        payload.setPostId(uuid(100));

        assertThatThrownBy(() -> listener.onContentEvent(new ContentContractEvent(
                "ce:post:published:missing-user", null, null, ContentEventTypes.POST_PUBLISHED,
                Instant.EPOCH, 1L, payload)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(ContentEventTypes.POST_PUBLISHED)
                .hasMessageContaining("ce:post:published:missing-user");

        verifyNoInteractions(walletRewardActionApi);
    }

    @Test
    void recognizedEventWithInvalidSourceMetadataShouldFailDelivery() {
        WalletRewardActionApi walletRewardActionApi = mock(WalletRewardActionApi.class);
        UserRewardKafkaListener listener = listener(walletRewardActionApi);
        PostPayload payload = new PostPayload();
        payload.setPostId(uuid(100));
        payload.setUserId(uuid(7));

        assertThatThrownBy(() -> listener.onContentEvent(new ContentContractEvent(
                " ", null, null, ContentEventTypes.POST_PUBLISHED, null, 0L, payload)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(ContentEventTypes.POST_PUBLISHED);

        verifyNoInteractions(walletRewardActionApi);
    }

    private UserRewardKafkaListener listener(WalletRewardActionApi walletRewardActionApi) {
        return new UserRewardKafkaListener(jsonCodec, new UserRewardApplicationService(walletRewardActionApi));
    }

    private static LikePayload likePayload(java.util.UUID actorUserId, java.util.UUID entityId, java.util.UUID entityUserId) {
        LikePayload payload = new LikePayload();
        payload.setActorUserId(actorUserId);
        payload.setEntityType(POST);
        payload.setEntityId(entityId);
        payload.setEntityUserId(entityUserId);
        payload.setRelationKey("like:" + actorUserId + ":" + POST + ":" + entityId);
        return payload;
    }
}
