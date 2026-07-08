package com.nowcoder.community.social.domain.service;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.social.domain.repository.BlockRepository;
import com.nowcoder.community.social.exception.SocialErrorCode;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BlockDomainServiceTest {

    @Test
    void validateBlockShouldRejectSelfWhenUuidValuesMatchButInstancesDiffer() {
        BlockDomainService service = new BlockDomainService();
        UUID userId = uuid(1);
        UUID targetUserId = UUID.fromString(userId.toString());

        assertThatThrownBy(() -> service.validateBlock(userId, targetUserId))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(SocialErrorCode.CANNOT_BLOCK_SELF));
    }

    @Test
    void isEitherBlockedShouldIgnoreSameUser() {
        BlockDomainService service = new BlockDomainService();
        BlockRepository repository = mock(BlockRepository.class);
        UUID userId = uuid(1);
        UUID sameUserDifferentInstance = UUID.fromString(userId.toString());

        assertThat(service.isEitherBlocked(userId, sameUserDifferentInstance, repository)).isFalse();

        verify(repository, never()).hasBlocked(userId, sameUserDifferentInstance);
    }

    @Test
    void isEitherBlockedShouldCheckBothDirections() {
        BlockDomainService service = new BlockDomainService();
        BlockRepository repository = mock(BlockRepository.class);
        when(repository.hasBlocked(uuid(1), uuid(2))).thenReturn(false);
        when(repository.hasBlocked(uuid(2), uuid(1))).thenReturn(true);

        assertThat(service.isEitherBlocked(uuid(1), uuid(2), repository)).isTrue();

        verify(repository).hasBlocked(uuid(1), uuid(2));
        verify(repository).hasBlocked(uuid(2), uuid(1));
    }
}
