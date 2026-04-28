package com.nowcoder.community.social.domain.service;

import com.nowcoder.community.common.constants.EntityTypes;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.social.domain.event.LikeChangedDomainEvent;
import com.nowcoder.community.social.domain.model.ResolvedSocialEntity;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LikeDomainServiceTest {

    @Test
    void resolveTargetStateShouldToggleWhenLikedIsNull() {
        LikeDomainService service = new LikeDomainService();

        assertThat(service.resolveTargetState(false, null)).isTrue();
        assertThat(service.resolveTargetState(true, null)).isFalse();
    }

    @Test
    void resolveTargetStateShouldUseRequestedStateWhenLikedIsPresent() {
        LikeDomainService service = new LikeDomainService();

        assertThat(service.resolveTargetState(false, Boolean.FALSE)).isFalse();
        assertThat(service.resolveTargetState(true, Boolean.TRUE)).isTrue();
    }

    @Test
    void validateLikeShouldRejectInvalidActorAndEntity() {
        LikeDomainService service = new LikeDomainService();

        assertThatThrownBy(() -> service.validateLike(null, EntityTypes.POST, uuid(1)))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.validateLike(uuid(1), 0, uuid(1)))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.validateLike(uuid(1), EntityTypes.POST, null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void likeChangedEventShouldCarryResolvedOwnerAndPost() {
        LikeDomainService service = new LikeDomainService();
        Instant createdAt = Instant.parse("2026-04-28T00:00:00Z");

        LikeChangedDomainEvent event = service.likeChangedEvent(
                uuid(1),
                EntityTypes.POST,
                uuid(10),
                new ResolvedSocialEntity(uuid(2), uuid(10)),
                true,
                createdAt
        );

        assertThat(event.actorUserId()).isEqualTo(uuid(1));
        assertThat(event.entityType()).isEqualTo(EntityTypes.POST);
        assertThat(event.entityId()).isEqualTo(uuid(10));
        assertThat(event.entityUserId()).isEqualTo(uuid(2));
        assertThat(event.postId()).isEqualTo(uuid(10));
        assertThat(event.liked()).isTrue();
        assertThat(event.createTime()).isEqualTo(createdAt);
    }
}
