package com.nowcoder.community.social.domain.service;

import com.nowcoder.community.common.constants.EntityTypes;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.social.domain.event.FollowCreatedDomainEvent;
import com.nowcoder.community.social.exception.SocialErrorCode;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FollowDomainServiceTest {

    @Test
    void validateFollowShouldRejectSelfWhenUuidValuesMatchButInstancesDiffer() {
        FollowDomainService service = new FollowDomainService();
        UUID actorUserId = uuid(1);
        UUID targetUserId = UUID.fromString(actorUserId.toString());

        assertThatThrownBy(() -> service.validateFollow(actorUserId, EntityTypes.USER, targetUserId))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(SocialErrorCode.CANNOT_FOLLOW_SELF));
    }

    @Test
    void validateFollowShouldRejectNonUserEntityType() {
        FollowDomainService service = new FollowDomainService();

        assertThatThrownBy(() -> service.validateFollow(uuid(1), EntityTypes.POST, uuid(2)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void validateUnfollowShouldRejectNonUserEntityType() {
        FollowDomainService service = new FollowDomainService();

        assertThatThrownBy(() -> service.validateUnfollow(uuid(1), EntityTypes.POST, uuid(2)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void followCreatedEventShouldUseTargetUserAsEntityOwner() {
        FollowDomainService service = new FollowDomainService();
        Instant createdAt = Instant.parse("2026-04-28T00:00:00Z");

        FollowCreatedDomainEvent event = service.followCreatedEvent(uuid(1), EntityTypes.USER, uuid(2), createdAt);

        assertThat(event.actorUserId()).isEqualTo(uuid(1));
        assertThat(event.entityType()).isEqualTo(EntityTypes.USER);
        assertThat(event.entityId()).isEqualTo(uuid(2));
        assertThat(event.entityUserId()).isEqualTo(uuid(2));
        assertThat(event.createTime()).isEqualTo(createdAt);
    }
}
