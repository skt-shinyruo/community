package com.nowcoder.community.social.application;

import com.nowcoder.community.social.block.BlockService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BlockQueryApplicationServiceTest {

    @Test
    void invalidOrSameUserIdsShouldShortCircuitToFalse() {
        BlockService blockService = mock(BlockService.class);
        BlockQueryApplicationService service = new BlockQueryApplicationService(blockService);

        assertThat(service.isEitherBlocked(0, 2)).isFalse();
        assertThat(service.isEitherBlocked(1, 0)).isFalse();
        assertThat(service.isEitherBlocked(3, 3)).isFalse();
    }

    @Test
    void validUserIdsShouldDelegateToBlockService() {
        BlockService blockService = mock(BlockService.class);
        when(blockService.isEitherBlocked(1, 2)).thenReturn(true);

        BlockQueryApplicationService service = new BlockQueryApplicationService(blockService);

        assertThat(service.isEitherBlocked(1, 2)).isTrue();
        verify(blockService).isEitherBlocked(1, 2);
    }
}
