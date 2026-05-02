package com.nowcoder.community.social.application;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.social.application.command.BlockCommand;
import com.nowcoder.community.social.domain.event.BlockRelationChangedDomainEvent;
import com.nowcoder.community.social.domain.event.SocialDomainEventPublisher;
import com.nowcoder.community.social.domain.repository.BlockRepository;
import com.nowcoder.community.social.domain.repository.FollowRepository;
import com.nowcoder.community.social.domain.service.BlockDomainService;
import com.nowcoder.community.social.exception.SocialErrorCode;
import com.nowcoder.community.social.infrastructure.event.InMemorySocialDomainEventPublisher;
import com.nowcoder.community.social.infrastructure.persistence.InMemoryBlockRepository;
import com.nowcoder.community.social.infrastructure.persistence.InMemoryFollowRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static com.nowcoder.community.common.constants.EntityTypes.USER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BlockApplicationServiceTest {

    private static final UUID USER_ID_1 = uuid(1);
    private static final UUID USER_ID_2 = uuid(2);

    @Test
    void blockApplicationServiceShouldExposeOwnerDomainConstructorWithoutImTypedFields() {
        assertThat(BlockApplicationService.class.getDeclaredConstructors())
                .anySatisfy(constructor -> assertThat(constructor.getParameterTypes()).containsExactly(
                        BlockRepository.class,
                        FollowRepository.class,
                        BlockDomainService.class,
                        SocialDomainEventPublisher.class
                ));
        assertThat(BlockApplicationService.class.getDeclaredFields())
                .extracting(Field::getType)
                .extracting(Class::getName)
                .doesNotContain("com.nowcoder.community.im.projection.ImPolicyChangePublisher");
    }

    @Test
    void blockShouldRejectSelfWhenUuidValuesMatchButInstancesDiffer() {
        InMemoryBlockRepository repo = new InMemoryBlockRepository();
        InMemorySocialDomainEventPublisher publisher = new InMemorySocialDomainEventPublisher();
        BlockApplicationService service = new BlockApplicationService(repo, new InMemoryFollowRepository(), new BlockDomainService(), publisher);
        UUID userId = uuid(1);
        UUID targetUserId = UUID.fromString(userId.toString());

        assertThat(targetUserId)
                .isEqualTo(userId)
                .isNotSameAs(userId);

        assertThatThrownBy(() -> service.block(new BlockCommand(userId, targetUserId)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(SocialErrorCode.CANNOT_BLOCK_SELF));

        assertThat(service.hasBlocked(userId, targetUserId)).isFalse();
        assertThat(publisher.snapshot()).isEmpty();
    }

    @Test
    void isEitherBlockedShouldIgnoreSameUserWhenUuidValuesMatchButInstancesDiffer() {
        InMemoryBlockRepository repo = new InMemoryBlockRepository();
        BlockApplicationService service = new BlockApplicationService(repo, new InMemoryFollowRepository(), new BlockDomainService(), new InMemorySocialDomainEventPublisher());
        UUID userId = uuid(1);
        UUID sameUserDifferentInstance = UUID.fromString(userId.toString());

        repo.block(userId, sameUserDifferentInstance);

        assertThat(service.isEitherBlocked(userId, sameUserDifferentInstance)).isFalse();
    }

    @Test
    void blockShouldPublishBlockRelationChangedEventWhenRelationChanges() {
        BlockRepository repository = mock(BlockRepository.class);
        FollowRepository followRepository = mock(FollowRepository.class);
        SocialDomainEventPublisher eventPublisher = mock(SocialDomainEventPublisher.class);
        when(repository.block(USER_ID_1, USER_ID_2)).thenReturn(true);

        BlockApplicationService service = new BlockApplicationService(repository, followRepository, new BlockDomainService(), eventPublisher);

        service.block(new BlockCommand(USER_ID_1, USER_ID_2));

        ArgumentCaptor<BlockRelationChangedDomainEvent> eventCaptor = ArgumentCaptor.forClass(BlockRelationChangedDomainEvent.class);
        verify(eventPublisher).publishBlockRelationChanged(eventCaptor.capture());
        BlockRelationChangedDomainEvent event = eventCaptor.getValue();
        assertThat(event.blockerUserId()).isEqualTo(USER_ID_1);
        assertThat(event.blockedUserId()).isEqualTo(USER_ID_2);
        assertThat(event.blocked()).isTrue();
    }

    @Test
    void blockShouldRemoveFollowRelationsInBothDirections() {
        InMemoryBlockRepository blockRepository = new InMemoryBlockRepository();
        InMemoryFollowRepository followRepository = new InMemoryFollowRepository();
        BlockApplicationService service = new BlockApplicationService(
                blockRepository,
                followRepository,
                new BlockDomainService(),
                new InMemorySocialDomainEventPublisher()
        );
        followRepository.follow(USER_ID_1, USER, USER_ID_2, 1000L);
        followRepository.follow(USER_ID_2, USER, USER_ID_1, 1001L);

        service.block(new BlockCommand(USER_ID_1, USER_ID_2));

        assertThat(followRepository.hasFollowed(USER_ID_1, USER, USER_ID_2)).isFalse();
        assertThat(followRepository.hasFollowed(USER_ID_2, USER, USER_ID_1)).isFalse();
        assertThat(followRepository.countFollowees(USER_ID_1, USER)).isZero();
        assertThat(followRepository.countFollowers(USER, USER_ID_1)).isZero();
    }

    @Test
    void blockShouldRemoveStaleFollowRelationsWhenBlockAlreadyExists() {
        InMemoryBlockRepository blockRepository = new InMemoryBlockRepository();
        InMemoryFollowRepository followRepository = new InMemoryFollowRepository();
        BlockApplicationService service = new BlockApplicationService(
                blockRepository,
                followRepository,
                new BlockDomainService(),
                new InMemorySocialDomainEventPublisher()
        );
        blockRepository.block(USER_ID_1, USER_ID_2);
        followRepository.follow(USER_ID_1, USER, USER_ID_2, 1000L);
        followRepository.follow(USER_ID_2, USER, USER_ID_1, 1001L);

        service.block(new BlockCommand(USER_ID_1, USER_ID_2));

        assertThat(followRepository.hasFollowed(USER_ID_1, USER, USER_ID_2)).isFalse();
        assertThat(followRepository.hasFollowed(USER_ID_2, USER, USER_ID_1)).isFalse();
    }
}
