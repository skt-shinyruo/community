package com.nowcoder.community.social.infrastructure.persistence;

import com.nowcoder.community.social.infrastructure.persistence.mapper.FollowMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
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

    private static final Instant TEST_NOW = Instant.parse("2026-08-10T12:34:56Z");
    private static final Clock TEST_CLOCK = Clock.fixed(TEST_NOW, ZoneOffset.UTC);

    @Test
    void followedStatusesBatchShouldUseOneMapperQueryAndFillMissingTargetsWithFalse() {
        FollowMapper mapper = mock(FollowMapper.class);
        MyBatisFollowRepository repository = new MyBatisFollowRepository(mapper, TEST_CLOCK);
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

    @Test
    void followShouldUseInjectedClockWhenCallerDoesNotSupplyATimestamp() {
        FollowMapper mapper = mock(FollowMapper.class);
        MyBatisFollowRepository repository = new MyBatisFollowRepository(mapper, TEST_CLOCK);
        UUID actorUserId = uuid(1);
        UUID targetUserId = uuid(2);
        when(mapper.insertFollow(
                org.mockito.ArgumentMatchers.eq(actorUserId),
                org.mockito.ArgumentMatchers.eq(USER),
                org.mockito.ArgumentMatchers.eq(targetUserId),
                org.mockito.ArgumentMatchers.any(Date.class)
        )).thenReturn(1);

        assertThat(repository.follow(actorUserId, USER, targetUserId, 0L)).isTrue();

        ArgumentCaptor<Date> createdAt = ArgumentCaptor.forClass(Date.class);
        verify(mapper).insertFollow(
                org.mockito.ArgumentMatchers.eq(actorUserId),
                org.mockito.ArgumentMatchers.eq(USER),
                org.mockito.ArgumentMatchers.eq(targetUserId),
                createdAt.capture()
        );
        assertThat(createdAt.getValue()).isEqualTo(Date.from(TEST_NOW));
    }

    @Test
    void cursorQueryShouldPassStableTimeAndTargetBoundaryToMapper() {
        FollowMapper mapper = mock(FollowMapper.class);
        MyBatisFollowRepository repository = new MyBatisFollowRepository(mapper, TEST_CLOCK);
        UUID ownerId = uuid(1);
        UUID boundaryId = uuid(9);
        when(mapper.listFolloweesAfterExcludingBlocked(
                ownerId, USER, Date.from(TEST_NOW), boundaryId, 21)).thenReturn(List.of());

        assertThat(repository.listFolloweesAfterExcludingBlocked(
                ownerId, USER, null, TEST_NOW, boundaryId, 21)).isEmpty();

        verify(mapper).listFolloweesAfterExcludingBlocked(
                ownerId, USER, Date.from(TEST_NOW), boundaryId, 21);
    }
}
