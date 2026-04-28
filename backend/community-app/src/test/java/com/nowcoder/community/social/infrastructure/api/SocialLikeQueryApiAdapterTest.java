package com.nowcoder.community.social.infrastructure.api;

import com.nowcoder.community.social.application.LikeApplicationService;
import org.junit.jupiter.api.Test;

import static com.nowcoder.community.common.constants.EntityTypes.POST;
import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SocialLikeQueryApiAdapterTest {

    @Test
    void countShouldDelegateToApplicationService() {
        LikeApplicationService likeApplicationService = mock(LikeApplicationService.class);
        when(likeApplicationService.count(POST, uuid(10))).thenReturn(7L);
        SocialLikeQueryApiAdapter adapter = new SocialLikeQueryApiAdapter(likeApplicationService);

        assertThat(adapter.count(POST, uuid(10))).isEqualTo(7);

        verify(likeApplicationService).count(POST, uuid(10));
    }

    @Test
    void statusAndUserLikeCountShouldDelegateToApplicationService() {
        LikeApplicationService likeApplicationService = mock(LikeApplicationService.class);
        when(likeApplicationService.isLiked(uuid(1), POST, uuid(10))).thenReturn(true);
        when(likeApplicationService.userLikeCount(uuid(2))).thenReturn(11L);
        SocialLikeQueryApiAdapter adapter = new SocialLikeQueryApiAdapter(likeApplicationService);

        assertThat(adapter.isLiked(uuid(1), POST, uuid(10))).isTrue();
        assertThat(adapter.userLikeCount(uuid(2))).isEqualTo(11);

        verify(likeApplicationService).isLiked(uuid(1), POST, uuid(10));
        verify(likeApplicationService).userLikeCount(uuid(2));
    }
}
