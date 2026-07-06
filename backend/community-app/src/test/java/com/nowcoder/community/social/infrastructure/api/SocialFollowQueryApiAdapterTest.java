package com.nowcoder.community.social.infrastructure.api;

import com.nowcoder.community.social.application.FollowApplicationService;
import org.junit.jupiter.api.Test;

import static com.nowcoder.community.common.constants.EntityTypes.USER;
import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SocialFollowQueryApiAdapterTest {

    @Test
    void followQueriesShouldDelegateToApplicationService() {
        FollowApplicationService followApplicationService = mock(FollowApplicationService.class);
        when(followApplicationService.hasFollowed(uuid(1), USER, uuid(2))).thenReturn(true);
        when(followApplicationService.followeeCount(uuid(1), USER)).thenReturn(3L);
        when(followApplicationService.followerCount(USER, uuid(2))).thenReturn(4L);
        when(followApplicationService.listFolloweeIds(uuid(1), 20)).thenReturn(java.util.List.of(uuid(2), uuid(3)));
        SocialFollowQueryApiAdapter adapter = new SocialFollowQueryApiAdapter(followApplicationService);

        assertThat(adapter.hasFollowed(uuid(1), USER, uuid(2))).isTrue();
        assertThat(adapter.followeeCount(uuid(1), USER)).isEqualTo(3);
        assertThat(adapter.followerCount(USER, uuid(2))).isEqualTo(4);
        assertThat(adapter.listFolloweeIds(uuid(1), 20)).containsExactly(uuid(2), uuid(3));

        verify(followApplicationService).hasFollowed(uuid(1), USER, uuid(2));
        verify(followApplicationService).followeeCount(uuid(1), USER);
        verify(followApplicationService).followerCount(USER, uuid(2));
        verify(followApplicationService).listFolloweeIds(uuid(1), 20);
    }
}
