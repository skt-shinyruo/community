package com.nowcoder.community.social.infrastructure.api;

import com.nowcoder.community.social.api.model.SocialBlockRelationView;
import com.nowcoder.community.social.application.BlockApplicationService;
import com.nowcoder.community.social.application.result.BlockRelationResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SocialBlockQueryApiAdapterTest {

    @Test
    void blockQueriesShouldDelegateToApplicationService() {
        BlockApplicationService blockApplicationService = mock(BlockApplicationService.class);
        when(blockApplicationService.hasBlocked(uuid(1), uuid(2))).thenReturn(true);
        when(blockApplicationService.isEitherBlocked(uuid(1), uuid(3))).thenReturn(false);
        SocialBlockQueryApiAdapter adapter = new SocialBlockQueryApiAdapter(blockApplicationService);

        assertThat(adapter.hasBlocked(uuid(1), uuid(2))).isTrue();
        assertThat(adapter.isEitherBlocked(uuid(1), uuid(3))).isFalse();

        verify(blockApplicationService).hasBlocked(uuid(1), uuid(2));
        verify(blockApplicationService).isEitherBlocked(uuid(1), uuid(3));
    }

    @Test
    void scanBlockRelationsAfterShouldMapApplicationResultsToApiModel() {
        BlockApplicationService blockApplicationService = mock(BlockApplicationService.class);
        when(blockApplicationService.scanBlockRelationsAfter(uuid(1), uuid(2), 10))
                .thenReturn(List.of(new BlockRelationResult(uuid(3), uuid(4))));
        SocialBlockQueryApiAdapter adapter = new SocialBlockQueryApiAdapter(blockApplicationService);

        List<SocialBlockRelationView> relations = adapter.scanBlockRelationsAfter(uuid(1), uuid(2), 10);

        assertThat(relations).containsExactly(new SocialBlockRelationView(uuid(3), uuid(4)));
        verify(blockApplicationService).scanBlockRelationsAfter(uuid(1), uuid(2), 10);
    }
}
