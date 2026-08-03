package com.nowcoder.community.social.infrastructure.persistence;

import com.nowcoder.community.social.infrastructure.persistence.mapper.FollowMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static com.nowcoder.community.common.constants.EntityTypes.USER;
import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MyBatisFollowRepositoryTest {

    @Test
    void followedStatusesBatchShouldUseOneMapperQueryAndFillMissingTargetsWithFalse() {
        FollowMapper mapper = mock(FollowMapper.class);
        MyBatisFollowRepository repository = new MyBatisFollowRepository(mapper);
        UUID actorUserId = uuid(1);
        UUID followedUserId = uuid(2);
        UUID otherUserId = uuid(3);
        when(mapper.selectFollowedEntityIds(
                org.mockito.ArgumentMatchers.eq(actorUserId),
                org.mockito.ArgumentMatchers.eq(USER),
                anyList()
        )).thenReturn(List.of(followedUserId));

        assertThat(repository.followedStatusesBatch(
                actorUserId,
                USER,
                List.of(followedUserId, otherUserId)
        )).containsEntry(followedUserId, true)
                .containsEntry(otherUserId, false);

        verify(mapper).selectFollowedEntityIds(
                org.mockito.ArgumentMatchers.eq(actorUserId),
                org.mockito.ArgumentMatchers.eq(USER),
                anyList()
        );
        verify(mapper, never()).countFollow(actorUserId, USER, followedUserId);
        verify(mapper, never()).countFollow(actorUserId, USER, otherUserId);
    }
}
