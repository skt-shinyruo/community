package com.nowcoder.community.social.infrastructure.event;

import com.nowcoder.community.common.constants.EntityTypes;
import com.nowcoder.community.social.contracts.event.BlockPayload;
import com.nowcoder.community.social.contracts.event.FollowPayload;
import com.nowcoder.community.social.contracts.event.LikePayload;
import com.nowcoder.community.social.contracts.event.SocialContractEvent;
import com.nowcoder.community.social.contracts.event.SocialEventTypes;
import com.nowcoder.community.social.domain.event.BlockRelationChangedDomainEvent;
import com.nowcoder.community.social.domain.event.FollowCreatedDomainEvent;
import com.nowcoder.community.social.domain.event.LikeChangedDomainEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class LocalSocialDomainEventPublisherTest {

    @Test
    void publishLikeChangedShouldMapCreatedEventToContractPayload() {
        ApplicationEventPublisher springPublisher = mock(ApplicationEventPublisher.class);
        LocalSocialDomainEventPublisher publisher = new LocalSocialDomainEventPublisher(springPublisher);
        Instant createdAt = Instant.parse("2026-04-28T00:00:00Z");

        publisher.publishLikeChanged(new LikeChangedDomainEvent(
                uuid(1), EntityTypes.POST, uuid(10), uuid(2), uuid(10), true, createdAt
        ));

        SocialContractEvent event = captureEvent(springPublisher);
        assertThat(event.eventId()).isNotBlank();
        assertThat(event.type()).isEqualTo(SocialEventTypes.LIKE_CREATED);
        assertThat(event.payload()).isInstanceOf(LikePayload.class);
        LikePayload payload = (LikePayload) event.payload();
        assertThat(payload.getActorUserId()).isEqualTo(uuid(1));
        assertThat(payload.getEntityType()).isEqualTo(EntityTypes.POST);
        assertThat(payload.getEntityId()).isEqualTo(uuid(10));
        assertThat(payload.getEntityUserId()).isEqualTo(uuid(2));
        assertThat(payload.getPostId()).isEqualTo(uuid(10));
        assertThat(payload.getCreateTime()).isEqualTo(createdAt);
    }

    @Test
    void publishLikeChangedShouldMapRemovedEventToContractPayload() {
        ApplicationEventPublisher springPublisher = mock(ApplicationEventPublisher.class);
        LocalSocialDomainEventPublisher publisher = new LocalSocialDomainEventPublisher(springPublisher);

        publisher.publishLikeChanged(new LikeChangedDomainEvent(
                uuid(1), EntityTypes.POST, uuid(10), uuid(2), uuid(10), false, Instant.EPOCH
        ));

        SocialContractEvent event = captureEvent(springPublisher);
        assertThat(event.type()).isEqualTo(SocialEventTypes.LIKE_REMOVED);
        assertThat(event.payload()).isInstanceOf(LikePayload.class);
    }

    @Test
    void publishFollowCreatedShouldMapContractPayload() {
        ApplicationEventPublisher springPublisher = mock(ApplicationEventPublisher.class);
        LocalSocialDomainEventPublisher publisher = new LocalSocialDomainEventPublisher(springPublisher);

        publisher.publishFollowCreated(new FollowCreatedDomainEvent(
                uuid(1), EntityTypes.USER, uuid(2), uuid(2), Instant.EPOCH
        ));

        SocialContractEvent event = captureEvent(springPublisher);
        assertThat(event.type()).isEqualTo(SocialEventTypes.FOLLOW_CREATED);
        assertThat(event.payload()).isInstanceOf(FollowPayload.class);
        FollowPayload payload = (FollowPayload) event.payload();
        assertThat(payload.getActorUserId()).isEqualTo(uuid(1));
        assertThat(payload.getEntityUserId()).isEqualTo(uuid(2));
    }

    @Test
    void publishBlockRelationChangedShouldMapContractPayload() {
        ApplicationEventPublisher springPublisher = mock(ApplicationEventPublisher.class);
        LocalSocialDomainEventPublisher publisher = new LocalSocialDomainEventPublisher(springPublisher);

        publisher.publishBlockRelationChanged(new BlockRelationChangedDomainEvent(uuid(1), uuid(2), true));

        SocialContractEvent event = captureEvent(springPublisher);
        assertThat(event.type()).isEqualTo(SocialEventTypes.BLOCK_RELATION_CHANGED);
        assertThat(event.payload()).isInstanceOf(BlockPayload.class);
        BlockPayload payload = (BlockPayload) event.payload();
        assertThat(payload.getBlockerUserId()).isEqualTo(uuid(1));
        assertThat(payload.getBlockedUserId()).isEqualTo(uuid(2));
        assertThat(payload.getBlocked()).isTrue();
    }

    private SocialContractEvent captureEvent(ApplicationEventPublisher springPublisher) {
        ArgumentCaptor<SocialContractEvent> captor = ArgumentCaptor.forClass(SocialContractEvent.class);
        verify(springPublisher).publishEvent(captor.capture());
        return captor.getValue();
    }
}
