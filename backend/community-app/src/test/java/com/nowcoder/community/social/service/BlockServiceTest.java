package com.nowcoder.community.social.service;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.social.block.BlockRepository;
import com.nowcoder.community.social.block.BlockService;
import com.nowcoder.community.social.block.InMemoryBlockRepository;
import com.nowcoder.community.social.contracts.event.BlockPayload;
import com.nowcoder.community.social.event.InMemorySocialEventPublisher;
import com.nowcoder.community.social.event.SocialEventPublisher;
import com.nowcoder.community.social.exception.SocialErrorCode;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BlockServiceTest {

    private static final UUID USER_ID_1 = uuid(1);
    private static final UUID USER_ID_2 = uuid(2);

    @Test
    void blockServiceShouldExposeOwnerDomainConstructorWithoutImTypedFields() {
        assertThat(BlockService.class.getDeclaredConstructors())
                .anySatisfy(constructor -> assertThat(constructor.getParameterTypes()).containsExactly(
                        BlockRepository.class,
                        SocialEventPublisher.class
                ));
        assertThat(BlockService.class.getDeclaredFields())
                .extracting(Field::getType)
                .extracting(Class::getName)
                .doesNotContain("com.nowcoder.community.im.projection.ImPolicyChangePublisher");
    }

    @Test
    void blockShouldRejectSelfWhenUuidValuesMatchButInstancesDiffer() {
        InMemoryBlockRepository repo = new InMemoryBlockRepository();
        InMemorySocialEventPublisher publisher = new InMemorySocialEventPublisher();
        BlockService service = new BlockService(repo, publisher);
        UUID userId = uuid(1);
        UUID targetUserId = UUID.fromString(userId.toString());

        assertThat(targetUserId)
                .isEqualTo(userId)
                .isNotSameAs(userId);

        assertThatThrownBy(() -> service.block(userId, targetUserId))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(SocialErrorCode.CANNOT_BLOCK_SELF));

        assertThat(service.hasBlocked(userId, targetUserId)).isFalse();
        assertThat(publisher.snapshot()).isEmpty();
    }

    @Test
    void isEitherBlockedShouldIgnoreSameUserWhenUuidValuesMatchButInstancesDiffer() {
        InMemoryBlockRepository repo = new InMemoryBlockRepository();
        BlockService service = new BlockService(repo, new InMemorySocialEventPublisher());
        UUID userId = uuid(1);
        UUID sameUserDifferentInstance = UUID.fromString(userId.toString());

        repo.block(userId, sameUserDifferentInstance);

        assertThat(service.isEitherBlocked(userId, sameUserDifferentInstance)).isFalse();
    }

    @Test
    void blockShouldPublishBlockRelationChangedEventWhenRelationChanges() {
        BlockRepository repository = mock(BlockRepository.class);
        SocialEventPublisher eventPublisher = mock(SocialEventPublisher.class);
        when(repository.block(USER_ID_1, USER_ID_2)).thenReturn(true);

        BlockService service = new BlockService(repository, eventPublisher);

        service.block(USER_ID_1, USER_ID_2);

        ArgumentCaptor<BlockPayload> payloadCaptor = ArgumentCaptor.forClass(BlockPayload.class);
        verify(eventPublisher).publishBlockRelationChanged(payloadCaptor.capture());
        BlockPayload payload = payloadCaptor.getValue();
        assertThat(payload.getBlockerUserId()).isEqualTo(USER_ID_1);
        assertThat(payload.getBlockedUserId()).isEqualTo(USER_ID_2);
        assertThat(payload.getBlocked()).isTrue();
    }
}
