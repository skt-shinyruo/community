package com.nowcoder.community.social.service;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.social.block.BlockService;
import com.nowcoder.community.social.block.InMemoryBlockRepository;
import com.nowcoder.community.social.event.InMemorySocialEventPublisher;
import com.nowcoder.community.social.exception.SocialErrorCode;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BlockServiceTest {

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
}
