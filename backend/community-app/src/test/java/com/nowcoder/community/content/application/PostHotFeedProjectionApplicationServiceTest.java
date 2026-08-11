package com.nowcoder.community.content.application;

import com.nowcoder.community.content.application.PostHotFeedProjectionApplicationService.ProjectPostHotFeedCommand;
import com.nowcoder.community.content.domain.model.DiscussPost;
import com.nowcoder.community.content.domain.repository.PostContentRepository;
import com.nowcoder.community.content.domain.service.PostHotnessDomainService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Date;
import java.util.List;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PostHotFeedProjectionApplicationServiceTest {

    @Test
    void postUpdatedShouldRecomputeHotnessAndUpsertBothFeeds() {
        PostContentRepository postContentRepository = mock(PostContentRepository.class);
        LikeQueryPort likeQueryPort = mock(LikeQueryPort.class);
        PostFeedCache postFeedCache = mock(PostFeedCache.class);
        PostSummaryCache postSummaryCache = mock(PostSummaryCache.class);
        PostDetailCache postDetailCache = mock(PostDetailCache.class);
        PostCounterCache postCounterCache = mock(PostCounterCache.class);
        PostHotnessDomainService postHotnessDomainService = mock(PostHotnessDomainService.class);
        PostHotFeedProjectionApplicationService service = newService(
                postContentRepository,
                likeQueryPort,
                postFeedCache,
                postSummaryCache,
                postDetailCache,
                postCounterCache,
                postHotnessDomainService,
                policyProperties()
        );

        DiscussPost post = post(uuid(200), uuid(10), 0, 12.0);
        when(postContentRepository.getByIdAllowDeleted(uuid(200))).thenReturn(post);
        when(likeQueryPort.countPostLikes(uuid(200))).thenReturn(41L);
        when(postHotnessDomainService.recomputeScore(post, 41L)).thenReturn(88.5);
        when(postContentRepository.updateScore(uuid(200), 88.5, 7L)).thenReturn(8L);

        service.project(new ProjectPostHotFeedCommand(
                uuid(200),
                uuid(10),
                "evt-post-updated",
                42L,
                PostProjectionVersionLane.POST,
                false
        ));

        verify(postFeedCache).writeRankVersion("hot-v2");
        verify(postContentRepository).updateScore(uuid(200), 88.5, 7L);
        verifyGlobalProjection(postFeedCache, uuid(200), 0, 88.5);
        verifyBoardProjection(postFeedCache, uuid(10), uuid(200), 0, 88.5);
        verify(postCounterCache).markDirty(uuid(200));
        verify(postSummaryCache).evictAll(List.of(uuid(200)), 7L, 8L);
        verify(postDetailCache).evict(uuid(200), 7L);
    }

    @Test
    void duplicateSourceEventShouldSkipProjectionWork() {
        PostContentRepository postContentRepository = mock(PostContentRepository.class);
        LikeQueryPort likeQueryPort = mock(LikeQueryPort.class);
        PostFeedCache postFeedCache = mock(PostFeedCache.class);
        PostSummaryCache postSummaryCache = mock(PostSummaryCache.class);
        PostDetailCache postDetailCache = mock(PostDetailCache.class);
        PostCounterCache postCounterCache = mock(PostCounterCache.class);
        PostHotnessDomainService postHotnessDomainService = mock(PostHotnessDomainService.class);
        HotFeedProjectionGuard projectionGuard = mock(HotFeedProjectionGuard.class);
        PostHotFeedProjectionApplicationService service = newService(
                postContentRepository,
                likeQueryPort,
                postFeedCache,
                postSummaryCache,
                postDetailCache,
                postCounterCache,
                postHotnessDomainService,
                policyProperties(),
                projectionGuard
        );
        HotFeedProjectionGuard.ProjectionAttempt attempt = HotFeedProjectionGuard.ProjectionAttempt.rejected(
                uuid(206),
                "evt-duplicate",
                48L,
                false
        );

        when(projectionGuard.tryBegin(uuid(206), "evt-duplicate", 48L, PostProjectionVersionLane.POST, false)).thenReturn(attempt);

        service.project(new ProjectPostHotFeedCommand(
                uuid(206),
                uuid(16),
                "evt-duplicate",
                48L,
                PostProjectionVersionLane.POST,
                false
        ));

        verify(projectionGuard).tryBegin(uuid(206), "evt-duplicate", 48L, PostProjectionVersionLane.POST, false);
        verifyNoInteractions(postContentRepository, likeQueryPort, postFeedCache, postSummaryCache, postDetailCache, postCounterCache, postHotnessDomainService);
    }

    @Test
    void staleSourceVersionShouldSkipProjectionWork() {
        PostContentRepository postContentRepository = mock(PostContentRepository.class);
        LikeQueryPort likeQueryPort = mock(LikeQueryPort.class);
        PostFeedCache postFeedCache = mock(PostFeedCache.class);
        PostSummaryCache postSummaryCache = mock(PostSummaryCache.class);
        PostDetailCache postDetailCache = mock(PostDetailCache.class);
        PostCounterCache postCounterCache = mock(PostCounterCache.class);
        PostHotnessDomainService postHotnessDomainService = mock(PostHotnessDomainService.class);
        HotFeedProjectionGuard projectionGuard = mock(HotFeedProjectionGuard.class);
        PostHotFeedProjectionApplicationService service = newService(
                postContentRepository,
                likeQueryPort,
                postFeedCache,
                postSummaryCache,
                postDetailCache,
                postCounterCache,
                postHotnessDomainService,
                policyProperties(),
                projectionGuard
        );
        HotFeedProjectionGuard.ProjectionAttempt attempt = HotFeedProjectionGuard.ProjectionAttempt.rejected(
                uuid(207),
                "evt-stale",
                47L,
                false
        );

        when(projectionGuard.tryBegin(uuid(207), "evt-stale", 47L, PostProjectionVersionLane.POST, false)).thenReturn(attempt);

        service.project(new ProjectPostHotFeedCommand(
                uuid(207),
                uuid(17),
                "evt-stale",
                47L,
                PostProjectionVersionLane.POST,
                false
        ));

        verify(projectionGuard).tryBegin(uuid(207), "evt-stale", 47L, PostProjectionVersionLane.POST, false);
        verifyNoInteractions(postContentRepository, likeQueryPort, postFeedCache, postSummaryCache, postDetailCache, postCounterCache, postHotnessDomainService);
    }

    @Test
    void supersededSourceVersionShouldAbortBeforeProjectionWrites() {
        PostContentRepository postContentRepository = mock(PostContentRepository.class);
        LikeQueryPort likeQueryPort = mock(LikeQueryPort.class);
        PostFeedCache postFeedCache = mock(PostFeedCache.class);
        PostSummaryCache postSummaryCache = mock(PostSummaryCache.class);
        PostDetailCache postDetailCache = mock(PostDetailCache.class);
        PostCounterCache postCounterCache = mock(PostCounterCache.class);
        PostHotnessDomainService postHotnessDomainService = mock(PostHotnessDomainService.class);
        HotFeedProjectionGuard projectionGuard = mock(HotFeedProjectionGuard.class);
        PostHotFeedProjectionApplicationService service = newService(
                postContentRepository,
                likeQueryPort,
                postFeedCache,
                postSummaryCache,
                postDetailCache,
                postCounterCache,
                postHotnessDomainService,
                policyProperties(),
                projectionGuard
        );
        HotFeedProjectionGuard.ProjectionAttempt attempt = HotFeedProjectionGuard.ProjectionAttempt.accepted(
                uuid(210),
                "evt-old",
                50L,
                false,
                "token-old"
        );
        DiscussPost post = post(uuid(210), uuid(20), 0, 10.0);
        when(projectionGuard.tryBegin(uuid(210), "evt-old", 50L, PostProjectionVersionLane.POST, false)).thenReturn(attempt);
        when(postContentRepository.getByIdAllowDeleted(uuid(210))).thenReturn(post);
        when(likeQueryPort.countPostLikes(uuid(210))).thenReturn(1L);
        when(postHotnessDomainService.recomputeScore(post, 1L)).thenReturn(12.0);
        when(projectionGuard.isCurrent(attempt)).thenReturn(false);

        service.project(new ProjectPostHotFeedCommand(
                uuid(210),
                uuid(20),
                "evt-old",
                50L,
                PostProjectionVersionLane.POST,
                false
        ));

        verify(projectionGuard).abort(attempt);
        verifyNoInteractions(postFeedCache, postSummaryCache, postDetailCache, postCounterCache);
    }

    @Test
    void supersededSourceVersionAfterScoreShouldAbortBeforeProjectionWrites() {
        PostContentRepository postContentRepository = mock(PostContentRepository.class);
        LikeQueryPort likeQueryPort = mock(LikeQueryPort.class);
        PostFeedCache postFeedCache = mock(PostFeedCache.class);
        PostSummaryCache postSummaryCache = mock(PostSummaryCache.class);
        PostDetailCache postDetailCache = mock(PostDetailCache.class);
        PostCounterCache postCounterCache = mock(PostCounterCache.class);
        PostHotnessDomainService postHotnessDomainService = mock(PostHotnessDomainService.class);
        HotFeedProjectionGuard projectionGuard = mock(HotFeedProjectionGuard.class);
        PostHotFeedProjectionApplicationService service = newService(
                postContentRepository,
                likeQueryPort,
                postFeedCache,
                postSummaryCache,
                postDetailCache,
                postCounterCache,
                postHotnessDomainService,
                policyProperties(),
                projectionGuard
        );
        HotFeedProjectionGuard.ProjectionAttempt attempt = HotFeedProjectionGuard.ProjectionAttempt.accepted(
                uuid(212),
                "evt-superseded",
                52L,
                false,
                "token-superseded"
        );
        DiscussPost post = post(uuid(212), uuid(22), 0, 10.0);
        when(projectionGuard.tryBegin(uuid(212), "evt-superseded", 52L, PostProjectionVersionLane.POST, false)).thenReturn(attempt);
        when(postContentRepository.getByIdAllowDeleted(uuid(212))).thenReturn(post);
        when(likeQueryPort.countPostLikes(uuid(212))).thenReturn(2L);
        when(postHotnessDomainService.recomputeScore(post, 2L)).thenReturn(14.0);
        when(projectionGuard.isCurrent(attempt)).thenReturn(true, false);

        service.project(new ProjectPostHotFeedCommand(
                uuid(212),
                uuid(22),
                "evt-superseded",
                52L,
                PostProjectionVersionLane.POST,
                false
        ));

        verify(projectionGuard).abort(attempt);
        verifyNoInteractions(postFeedCache, postSummaryCache, postDetailCache, postCounterCache);
    }

    @Test
    void supersededSourceVersionAfterScoreCasShouldAbortBeforeCacheWrites() {
        PostContentRepository postContentRepository = mock(PostContentRepository.class);
        LikeQueryPort likeQueryPort = mock(LikeQueryPort.class);
        PostFeedCache postFeedCache = mock(PostFeedCache.class);
        PostSummaryCache postSummaryCache = mock(PostSummaryCache.class);
        PostDetailCache postDetailCache = mock(PostDetailCache.class);
        PostCounterCache postCounterCache = mock(PostCounterCache.class);
        PostHotnessDomainService postHotnessDomainService = mock(PostHotnessDomainService.class);
        HotFeedProjectionGuard projectionGuard = mock(HotFeedProjectionGuard.class);
        PostHotFeedProjectionTransactionOperations transactionOperations =
                mock(PostHotFeedProjectionTransactionOperations.class);
        HotFeedProjectionCompletion projectionCompletion = mock(HotFeedProjectionCompletion.class);
        PostHotFeedProjectionApplicationService service = newService(
                postContentRepository,
                likeQueryPort,
                postFeedCache,
                postSummaryCache,
                postDetailCache,
                postCounterCache,
                postHotnessDomainService,
                policyProperties(),
                projectionGuard,
                transactionOperations,
                projectionCompletion
        );
        HotFeedProjectionGuard.ProjectionAttempt attempt = HotFeedProjectionGuard.ProjectionAttempt.accepted(
                uuid(213),
                "evt-superseded-after-cas",
                53L,
                PostProjectionVersionLane.POST,
                false,
                "token-superseded-after-cas"
        );
        DiscussPost post = post(uuid(213), uuid(23), 0, 10.0);
        when(projectionGuard.tryBegin(
                uuid(213),
                "evt-superseded-after-cas",
                53L,
                PostProjectionVersionLane.POST,
                false
        )).thenReturn(attempt);
        when(postContentRepository.getByIdAllowDeleted(uuid(213))).thenReturn(post);
        when(likeQueryPort.countPostLikes(uuid(213))).thenReturn(2L);
        when(postHotnessDomainService.recomputeScore(post, 2L)).thenReturn(14.0);
        when(projectionGuard.isCurrent(attempt)).thenReturn(true, true, false);

        service.project(new ProjectPostHotFeedCommand(
                uuid(213),
                uuid(23),
                "evt-superseded-after-cas",
                53L,
                PostProjectionVersionLane.POST,
                false
        ));

        verify(transactionOperations).updateScore(uuid(213), 14.0, 7L);
        verify(projectionGuard).abort(attempt);
        verifyNoInteractions(postFeedCache, postSummaryCache, postDetailCache, postCounterCache, projectionCompletion);
    }

    @Test
    void successfulProjectionShouldCommitSourceAttemptAfterWrites() {
        PostContentRepository postContentRepository = mock(PostContentRepository.class);
        LikeQueryPort likeQueryPort = mock(LikeQueryPort.class);
        PostFeedCache postFeedCache = mock(PostFeedCache.class);
        PostSummaryCache postSummaryCache = mock(PostSummaryCache.class);
        PostDetailCache postDetailCache = mock(PostDetailCache.class);
        PostCounterCache postCounterCache = mock(PostCounterCache.class);
        PostHotnessDomainService postHotnessDomainService = mock(PostHotnessDomainService.class);
        HotFeedProjectionGuard projectionGuard = mock(HotFeedProjectionGuard.class);
        PostHotFeedProjectionApplicationService service = newService(
                postContentRepository,
                likeQueryPort,
                postFeedCache,
                postSummaryCache,
                postDetailCache,
                postCounterCache,
                postHotnessDomainService,
                policyProperties(),
                projectionGuard
        );
        HotFeedProjectionGuard.ProjectionAttempt attempt = HotFeedProjectionGuard.ProjectionAttempt.accepted(
                uuid(211),
                "evt-current",
                51L,
                false,
                "token-current"
        );
        DiscussPost post = post(uuid(211), uuid(21), 0, 10.0);
        when(projectionGuard.tryBegin(uuid(211), "evt-current", 51L, PostProjectionVersionLane.POST, false)).thenReturn(attempt);
        when(postContentRepository.getByIdAllowDeleted(uuid(211))).thenReturn(post);
        when(likeQueryPort.countPostLikes(uuid(211))).thenReturn(2L);
        when(postHotnessDomainService.recomputeScore(post, 2L)).thenReturn(14.0);
        when(postContentRepository.updateScore(uuid(211), 14.0, 7L)).thenReturn(8L);
        when(projectionGuard.isCurrent(attempt)).thenReturn(true);

        service.project(new ProjectPostHotFeedCommand(
                uuid(211),
                uuid(21),
                "evt-current",
                51L,
                PostProjectionVersionLane.POST,
                false
        ));

        verifyGlobalProjection(postFeedCache, uuid(211), 0, 14.0);
        verify(projectionGuard).commit(attempt);
    }

    @Test
    void outOfOrderProjectionShouldNotRegressVersion() {
        PostContentRepository postContentRepository = mock(PostContentRepository.class);
        LikeQueryPort likeQueryPort = mock(LikeQueryPort.class);
        PostFeedCache postFeedCache = mock(PostFeedCache.class);
        PostSummaryCache postSummaryCache = mock(PostSummaryCache.class);
        PostDetailCache postDetailCache = mock(PostDetailCache.class);
        PostCounterCache postCounterCache = mock(PostCounterCache.class);
        PostHotnessDomainService postHotnessDomainService = mock(PostHotnessDomainService.class);
        HotFeedProjectionGuard projectionGuard = mock(HotFeedProjectionGuard.class);
        PostHotFeedProjectionApplicationService service = newService(
                postContentRepository,
                likeQueryPort,
                postFeedCache,
                postSummaryCache,
                postDetailCache,
                postCounterCache,
                postHotnessDomainService,
                policyProperties(),
                projectionGuard
        );
        HotFeedProjectionGuard.ProjectionAttempt accepted = HotFeedProjectionGuard.ProjectionAttempt.accepted(
                uuid(230),
                "evt-new",
                20L,
                false,
                "token-new"
        );
        HotFeedProjectionGuard.ProjectionAttempt stale = HotFeedProjectionGuard.ProjectionAttempt.rejected(
                uuid(230),
                "evt-old",
                10L,
                false
        );
        DiscussPost post = post(uuid(230), uuid(30), 0, 10.0);
        when(projectionGuard.tryBegin(uuid(230), "evt-new", 20L, PostProjectionVersionLane.POST, false)).thenReturn(accepted);
        when(projectionGuard.tryBegin(uuid(230), "evt-old", 10L, PostProjectionVersionLane.POST, false)).thenReturn(stale);
        when(postContentRepository.getByIdAllowDeleted(uuid(230))).thenReturn(post);
        when(likeQueryPort.countPostLikes(uuid(230))).thenReturn(2L);
        when(postHotnessDomainService.recomputeScore(post, 2L)).thenReturn(14.0);
        when(postContentRepository.updateScore(uuid(230), 14.0, 7L)).thenReturn(8L);
        when(projectionGuard.isCurrent(accepted)).thenReturn(true);

        service.project(new ProjectPostHotFeedCommand(
                uuid(230), uuid(30), "evt-new", 20L, PostProjectionVersionLane.POST, false));
        service.project(new ProjectPostHotFeedCommand(
                uuid(230), uuid(30), "evt-old", 10L, PostProjectionVersionLane.POST, false));

        verifyGlobalProjection(postFeedCache, uuid(230), 0, 14.0);
        verify(postHotnessDomainService, times(1)).recomputeScore(post, 2L);
        verify(projectionGuard).commit(accepted);
        verify(projectionGuard, never()).commit(stale);
    }

    @Test
    void blankSourceEventIdShouldSkipProjectionWork() {
        PostContentRepository postContentRepository = mock(PostContentRepository.class);
        LikeQueryPort likeQueryPort = mock(LikeQueryPort.class);
        PostFeedCache postFeedCache = mock(PostFeedCache.class);
        PostSummaryCache postSummaryCache = mock(PostSummaryCache.class);
        PostDetailCache postDetailCache = mock(PostDetailCache.class);
        PostCounterCache postCounterCache = mock(PostCounterCache.class);
        PostHotnessDomainService postHotnessDomainService = mock(PostHotnessDomainService.class);
        HotFeedProjectionGuard projectionGuard = mock(HotFeedProjectionGuard.class);
        PostHotFeedProjectionApplicationService service = newService(
                postContentRepository,
                likeQueryPort,
                postFeedCache,
                postSummaryCache,
                postDetailCache,
                postCounterCache,
                postHotnessDomainService,
                policyProperties(),
                projectionGuard
        );

        service.project(new ProjectPostHotFeedCommand(
                uuid(208), uuid(18), " ", 49L, PostProjectionVersionLane.POST, false));

        verifyNoInteractions(projectionGuard, postContentRepository, likeQueryPort, postFeedCache, postSummaryCache, postDetailCache, postCounterCache, postHotnessDomainService);
    }

    @Test
    void nonPositiveSourceVersionShouldSkipProjectionWork() {
        PostContentRepository postContentRepository = mock(PostContentRepository.class);
        LikeQueryPort likeQueryPort = mock(LikeQueryPort.class);
        PostFeedCache postFeedCache = mock(PostFeedCache.class);
        PostSummaryCache postSummaryCache = mock(PostSummaryCache.class);
        PostDetailCache postDetailCache = mock(PostDetailCache.class);
        PostCounterCache postCounterCache = mock(PostCounterCache.class);
        PostHotnessDomainService postHotnessDomainService = mock(PostHotnessDomainService.class);
        HotFeedProjectionGuard projectionGuard = mock(HotFeedProjectionGuard.class);
        PostHotFeedProjectionApplicationService service = newService(
                postContentRepository,
                likeQueryPort,
                postFeedCache,
                postSummaryCache,
                postDetailCache,
                postCounterCache,
                postHotnessDomainService,
                policyProperties(),
                projectionGuard
        );

        service.project(new ProjectPostHotFeedCommand(
                uuid(209), uuid(19), "evt-invalid-version", 0L, PostProjectionVersionLane.POST, false));

        verifyNoInteractions(projectionGuard, postContentRepository, likeQueryPort, postFeedCache, postSummaryCache, postDetailCache, postCounterCache, postHotnessDomainService);
    }

    @Test
    void postUpdatedShouldClearExistingBoardMembershipBeforeUpsertingCurrentBoard() {
        PostContentRepository postContentRepository = mock(PostContentRepository.class);
        LikeQueryPort likeQueryPort = mock(LikeQueryPort.class);
        PostFeedCache postFeedCache = mock(PostFeedCache.class);
        PostSummaryCache postSummaryCache = mock(PostSummaryCache.class);
        PostDetailCache postDetailCache = mock(PostDetailCache.class);
        PostCounterCache postCounterCache = mock(PostCounterCache.class);
        PostHotnessDomainService postHotnessDomainService = mock(PostHotnessDomainService.class);
        PostHotFeedProjectionApplicationService service = newService(
                postContentRepository,
                likeQueryPort,
                postFeedCache,
                postSummaryCache,
                postDetailCache,
                postCounterCache,
                postHotnessDomainService,
                policyProperties()
        );

        DiscussPost post = post(uuid(203), uuid(13), 0, 15.0);
        when(postContentRepository.getByIdAllowDeleted(uuid(203))).thenReturn(post);
        when(likeQueryPort.countPostLikes(uuid(203))).thenReturn(5L);
        when(postHotnessDomainService.recomputeScore(post, 5L)).thenReturn(18.0);
        when(postContentRepository.updateScore(uuid(203), 18.0, 7L)).thenReturn(8L);

        service.project(new ProjectPostHotFeedCommand(
                uuid(203),
                uuid(13),
                "evt-post-updated",
                43L,
                PostProjectionVersionLane.POST,
                false
        ));

        verify(postFeedCache).remove(uuid(203), null, 7L);
        verifyBoardProjection(postFeedCache, uuid(13), uuid(203), 0, 18.0);
    }

    @Test
    void deletedOwnerFactShouldTerminallyFenceEveryReadModel() {
        PostContentRepository postContentRepository = mock(PostContentRepository.class);
        LikeQueryPort likeQueryPort = mock(LikeQueryPort.class);
        PostFeedCache postFeedCache = mock(PostFeedCache.class);
        PostSummaryCache postSummaryCache = mock(PostSummaryCache.class);
        PostDetailCache postDetailCache = mock(PostDetailCache.class);
        PostCounterCache postCounterCache = mock(PostCounterCache.class);
        PostHotnessDomainService postHotnessDomainService = mock(PostHotnessDomainService.class);
        PostHotFeedProjectionApplicationService service = newService(
                postContentRepository,
                likeQueryPort,
                postFeedCache,
                postSummaryCache,
                postDetailCache,
                postCounterCache,
                postHotnessDomainService,
                policyProperties()
        );

        DiscussPost deleted = post(uuid(201), uuid(11), 2, 3.0);
        when(postContentRepository.getByIdAllowDeleted(uuid(201))).thenReturn(deleted);

        service.project(new ProjectPostHotFeedCommand(
                uuid(201),
                uuid(11),
                "evt-post-deleted",
                44L,
                PostProjectionVersionLane.POST,
                false
        ));

        verify(postFeedCache).writeRankVersion("hot-v2");
        verify(postFeedCache).terminalRemove(uuid(201), uuid(11), 7L);
        verify(postSummaryCache).terminalEvict(uuid(201), 7L);
        verify(postDetailCache).terminalEvict(uuid(201), 7L);
        verifyNoInteractions(likeQueryPort, postHotnessDomainService);
    }

    @Test
    void missingOwnerFactShouldAlsoTerminallyFenceEveryReadModel() {
        PostContentRepository postContentRepository = mock(PostContentRepository.class);
        LikeQueryPort likeQueryPort = mock(LikeQueryPort.class);
        PostFeedCache postFeedCache = mock(PostFeedCache.class);
        PostSummaryCache postSummaryCache = mock(PostSummaryCache.class);
        PostDetailCache postDetailCache = mock(PostDetailCache.class);
        PostCounterCache postCounterCache = mock(PostCounterCache.class);
        PostHotnessDomainService postHotnessDomainService = mock(PostHotnessDomainService.class);
        PostHotFeedProjectionApplicationService service = newService(
                postContentRepository,
                likeQueryPort,
                postFeedCache,
                postSummaryCache,
                postDetailCache,
                postCounterCache,
                postHotnessDomainService,
                policyProperties()
        );
        UUID postId = uuid(234);
        UUID boardId = uuid(34);
        when(postContentRepository.getByIdAllowDeleted(postId)).thenReturn(null);

        service.project(new ProjectPostHotFeedCommand(
                postId,
                boardId,
                "evt-post-missing",
                9L,
                PostProjectionVersionLane.POST,
                false
        ));

        verify(postFeedCache).terminalRemove(postId, boardId, 9L);
        verify(postSummaryCache).terminalEvict(postId, 9L);
        verify(postDetailCache).terminalEvict(postId, 9L);
        verify(postFeedCache, never()).remove(postId, null);
        verify(postSummaryCache, never()).evictAll(List.of(postId));
        verify(postDetailCache, never()).evict(postId);
    }

    @Test
    void terminalDeletionShouldEvictWithoutReadingCurrentFactsAndCommitAfterTransaction() {
        PostContentRepository postContentRepository = mock(PostContentRepository.class);
        LikeQueryPort likeQueryPort = mock(LikeQueryPort.class);
        PostFeedCache postFeedCache = mock(PostFeedCache.class);
        PostSummaryCache postSummaryCache = mock(PostSummaryCache.class);
        PostDetailCache postDetailCache = mock(PostDetailCache.class);
        PostCounterCache postCounterCache = mock(PostCounterCache.class);
        PostHotnessDomainService postHotnessDomainService = mock(PostHotnessDomainService.class);
        HotFeedProjectionGuard projectionGuard = mock(HotFeedProjectionGuard.class);
        HotFeedProjectionCompletion projectionCompletion = mock(HotFeedProjectionCompletion.class);
        PostHotFeedProjectionApplicationService service = newService(
                postContentRepository,
                likeQueryPort,
                postFeedCache,
                postSummaryCache,
                postDetailCache,
                postCounterCache,
                postHotnessDomainService,
                policyProperties(),
                projectionGuard,
                projectionCompletion
        );
        HotFeedProjectionGuard.ProjectionAttempt attempt = HotFeedProjectionGuard.ProjectionAttempt.accepted(
                uuid(231),
                "evt-terminal-delete",
                5L,
                true,
                "token-terminal"
        );
        DiscussPost stillActive = post(uuid(231), uuid(31), 0, 15.0);
        when(postContentRepository.getByIdAllowDeleted(uuid(231))).thenReturn(stillActive);
        when(projectionGuard.tryBegin(uuid(231), "evt-terminal-delete", 5L, PostProjectionVersionLane.POST, true)).thenReturn(attempt);
        when(projectionGuard.isCurrent(attempt)).thenReturn(true);

        service.project(new ProjectPostHotFeedCommand(
                uuid(231),
                uuid(31),
                "evt-terminal-delete",
                5L,
                PostProjectionVersionLane.POST,
                true
        ));

        verify(postFeedCache).terminalRemove(uuid(231), uuid(31), 5L);
        verify(postSummaryCache).terminalEvict(uuid(231), 5L);
        verify(postDetailCache).terminalEvict(uuid(231), 5L);
        verifyNoInteractions(postContentRepository, likeQueryPort, postCounterCache, postHotnessDomainService);
        ArgumentCaptor<Runnable> committedAction = ArgumentCaptor.forClass(Runnable.class);
        ArgumentCaptor<Runnable> rolledBackAction = ArgumentCaptor.forClass(Runnable.class);
        verify(projectionCompletion).afterTransaction(committedAction.capture(), rolledBackAction.capture());
        verify(projectionGuard, never()).commit(attempt);

        committedAction.getValue().run();

        verify(projectionGuard).commit(attempt);
        verify(projectionGuard, never()).abort(attempt);
    }

    @Test
    void terminalDeletionRollbackShouldAbortAttemptWithoutCommit() {
        PostContentRepository postContentRepository = mock(PostContentRepository.class);
        LikeQueryPort likeQueryPort = mock(LikeQueryPort.class);
        PostFeedCache postFeedCache = mock(PostFeedCache.class);
        PostSummaryCache postSummaryCache = mock(PostSummaryCache.class);
        PostDetailCache postDetailCache = mock(PostDetailCache.class);
        PostCounterCache postCounterCache = mock(PostCounterCache.class);
        PostHotnessDomainService postHotnessDomainService = mock(PostHotnessDomainService.class);
        HotFeedProjectionGuard projectionGuard = mock(HotFeedProjectionGuard.class);
        HotFeedProjectionCompletion projectionCompletion = mock(HotFeedProjectionCompletion.class);
        PostHotFeedProjectionApplicationService service = newService(
                postContentRepository,
                likeQueryPort,
                postFeedCache,
                postSummaryCache,
                postDetailCache,
                postCounterCache,
                postHotnessDomainService,
                policyProperties(),
                projectionGuard,
                projectionCompletion
        );
        HotFeedProjectionGuard.ProjectionAttempt attempt = HotFeedProjectionGuard.ProjectionAttempt.accepted(
                uuid(232),
                "evt-terminal-delete-rollback",
                4L,
                true,
                "token-terminal-rollback"
        );
        DiscussPost stillActive = post(uuid(232), uuid(32), 0, 16.0);
        when(postContentRepository.getByIdAllowDeleted(uuid(232))).thenReturn(stillActive);
        when(projectionGuard.tryBegin(uuid(232), "evt-terminal-delete-rollback", 4L, PostProjectionVersionLane.POST, true)).thenReturn(attempt);
        when(projectionGuard.isCurrent(attempt)).thenReturn(true);

        service.project(new ProjectPostHotFeedCommand(
                uuid(232),
                uuid(32),
                "evt-terminal-delete-rollback",
                4L,
                PostProjectionVersionLane.POST,
                true
        ));

        ArgumentCaptor<Runnable> committedAction = ArgumentCaptor.forClass(Runnable.class);
        ArgumentCaptor<Runnable> rolledBackAction = ArgumentCaptor.forClass(Runnable.class);
        verify(projectionCompletion).afterTransaction(committedAction.capture(), rolledBackAction.capture());
        verifyNoInteractions(postContentRepository, likeQueryPort, postCounterCache, postHotnessDomainService);

        rolledBackAction.getValue().run();

        verify(projectionGuard).abort(attempt);
        verify(projectionGuard, never()).commit(attempt);
    }

    @Test
    void terminalDeletionSinkFailureShouldAbortWithoutSchedulingGuardCommit() {
        PostContentRepository postContentRepository = mock(PostContentRepository.class);
        LikeQueryPort likeQueryPort = mock(LikeQueryPort.class);
        PostFeedCache postFeedCache = mock(PostFeedCache.class);
        PostSummaryCache postSummaryCache = mock(PostSummaryCache.class);
        PostDetailCache postDetailCache = mock(PostDetailCache.class);
        PostCounterCache postCounterCache = mock(PostCounterCache.class);
        PostHotnessDomainService postHotnessDomainService = mock(PostHotnessDomainService.class);
        HotFeedProjectionGuard projectionGuard = mock(HotFeedProjectionGuard.class);
        HotFeedProjectionCompletion projectionCompletion = mock(HotFeedProjectionCompletion.class);
        PostHotFeedProjectionApplicationService service = newService(
                postContentRepository,
                likeQueryPort,
                postFeedCache,
                postSummaryCache,
                postDetailCache,
                postCounterCache,
                postHotnessDomainService,
                policyProperties(),
                projectionGuard,
                projectionCompletion
        );
        HotFeedProjectionGuard.ProjectionAttempt attempt = HotFeedProjectionGuard.ProjectionAttempt.accepted(
                uuid(233),
                "evt-terminal-delete-failure",
                3L,
                true,
                "token-terminal-failure"
        );
        when(projectionGuard.tryBegin(uuid(233), "evt-terminal-delete-failure", 3L, PostProjectionVersionLane.POST, true)).thenReturn(attempt);
        when(projectionGuard.isCurrent(attempt)).thenReturn(true);
        doThrow(new IllegalStateException("summary terminal fence unavailable"))
                .when(postSummaryCache).terminalEvict(uuid(233), 3L);

        assertThatThrownBy(() -> service.project(new ProjectPostHotFeedCommand(
                uuid(233),
                uuid(33),
                "evt-terminal-delete-failure",
                3L,
                PostProjectionVersionLane.POST,
                true
        )))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("summary terminal fence unavailable");

        verify(postFeedCache).terminalRemove(uuid(233), uuid(33), 3L);
        verify(postSummaryCache).terminalEvict(uuid(233), 3L);
        verify(postDetailCache, never()).terminalEvict(uuid(233), 3L);
        verify(projectionGuard).abort(attempt);
        verify(projectionGuard, never()).commit(attempt);
        verifyNoInteractions(projectionCompletion);
    }

    @Test
    void socialLikeSignalShouldUseCurrentPostBoardWhenCommandBoardIdMissing() {
        PostContentRepository postContentRepository = mock(PostContentRepository.class);
        LikeQueryPort likeQueryPort = mock(LikeQueryPort.class);
        PostFeedCache postFeedCache = mock(PostFeedCache.class);
        PostSummaryCache postSummaryCache = mock(PostSummaryCache.class);
        PostDetailCache postDetailCache = mock(PostDetailCache.class);
        PostCounterCache postCounterCache = mock(PostCounterCache.class);
        PostHotnessDomainService postHotnessDomainService = mock(PostHotnessDomainService.class);
        PostHotFeedProjectionApplicationService service = newService(
                postContentRepository,
                likeQueryPort,
                postFeedCache,
                postSummaryCache,
                postDetailCache,
                postCounterCache,
                postHotnessDomainService,
                policyProperties()
        );

        DiscussPost post = post(uuid(202), uuid(12), 0, 20.0);
        when(postContentRepository.getByIdAllowDeleted(uuid(202))).thenReturn(post);
        when(likeQueryPort.countPostLikes(uuid(202))).thenReturn(9L);
        when(postHotnessDomainService.recomputeScore(post, 9L)).thenReturn(31.0);
        when(postContentRepository.updateScore(uuid(202), 31.0, 7L)).thenReturn(8L);

        service.project(new ProjectPostHotFeedCommand(
                uuid(202),
                null,
                "evt-like-created",
                45L,
                PostProjectionVersionLane.POST,
                false
        ));

        verifyGlobalProjection(postFeedCache, uuid(202), 0, 31.0);
        verifyBoardProjection(postFeedCache, uuid(12), uuid(202), 0, 31.0);
    }

    @Test
    void postUpdatedShouldUseCurrentPersistedBoardWhenCommandBoardIdIsStale() {
        PostContentRepository postContentRepository = mock(PostContentRepository.class);
        LikeQueryPort likeQueryPort = mock(LikeQueryPort.class);
        PostFeedCache postFeedCache = mock(PostFeedCache.class);
        PostSummaryCache postSummaryCache = mock(PostSummaryCache.class);
        PostDetailCache postDetailCache = mock(PostDetailCache.class);
        PostCounterCache postCounterCache = mock(PostCounterCache.class);
        PostHotnessDomainService postHotnessDomainService = mock(PostHotnessDomainService.class);
        PostHotFeedProjectionApplicationService service = newService(
                postContentRepository,
                likeQueryPort,
                postFeedCache,
                postSummaryCache,
                postDetailCache,
                postCounterCache,
                postHotnessDomainService,
                policyProperties()
        );

        DiscussPost post = post(uuid(204), uuid(14), 0, 22.0);
        when(postContentRepository.getByIdAllowDeleted(uuid(204))).thenReturn(post);
        when(likeQueryPort.countPostLikes(uuid(204))).thenReturn(7L);
        when(postHotnessDomainService.recomputeScore(post, 7L)).thenReturn(25.0);
        when(postContentRepository.updateScore(uuid(204), 25.0, 7L)).thenReturn(8L);

        service.project(new ProjectPostHotFeedCommand(
                uuid(204),
                uuid(99),
                "evt-post-updated",
                46L,
                PostProjectionVersionLane.POST,
                false
        ));

        verify(postFeedCache).remove(uuid(204), null, 7L);
        verifyBoardProjection(postFeedCache, uuid(14), uuid(204), 0, 25.0);
    }

    @Test
    void wonderfulPostShouldRemainVisibleInHotFeeds() {
        PostContentRepository postContentRepository = mock(PostContentRepository.class);
        LikeQueryPort likeQueryPort = mock(LikeQueryPort.class);
        PostFeedCache postFeedCache = mock(PostFeedCache.class);
        PostSummaryCache postSummaryCache = mock(PostSummaryCache.class);
        PostDetailCache postDetailCache = mock(PostDetailCache.class);
        PostCounterCache postCounterCache = mock(PostCounterCache.class);
        PostHotnessDomainService postHotnessDomainService = mock(PostHotnessDomainService.class);
        PostHotFeedProjectionApplicationService service = newService(
                postContentRepository,
                likeQueryPort,
                postFeedCache,
                postSummaryCache,
                postDetailCache,
                postCounterCache,
                postHotnessDomainService,
                policyProperties()
        );

        DiscussPost post = post(uuid(205), uuid(15), 1, 42.0);
        when(postContentRepository.getByIdAllowDeleted(uuid(205))).thenReturn(post);
        when(likeQueryPort.countPostLikes(uuid(205))).thenReturn(9L);
        when(postHotnessDomainService.recomputeScore(post, 9L)).thenReturn(51.0);
        when(postContentRepository.updateScore(uuid(205), 51.0, 7L)).thenReturn(8L);

        service.project(new ProjectPostHotFeedCommand(
                uuid(205),
                uuid(15),
                "ce:1",
                47L,
                PostProjectionVersionLane.POST,
                false
        ));

        verify(postFeedCache).writeRankVersion("hot-v2");
        verify(postFeedCache).remove(uuid(205), null, 7L);
        verifyGlobalProjection(postFeedCache, uuid(205), 0, 51.0);
        verifyBoardProjection(postFeedCache, uuid(15), uuid(205), 0, 51.0);
        verify(postCounterCache).markDirty(uuid(205));
    }

    private static ContentFeedPolicyProperties policyProperties() {
        ContentFeedPolicyProperties properties = new ContentFeedPolicyProperties();
        properties.setHotRankVersion("hot-v2");
        return properties;
    }

    private static PostHotFeedProjectionApplicationService newService(
            PostContentRepository postContentRepository,
            LikeQueryPort likeQueryPort,
            PostFeedCache postFeedCache,
            PostSummaryCache postSummaryCache,
            PostDetailCache postDetailCache,
            PostCounterCache postCounterCache,
            PostHotnessDomainService postHotnessDomainService,
            ContentFeedPolicyProperties policyProperties
    ) {
        return newService(
                postContentRepository,
                likeQueryPort,
                postFeedCache,
                postSummaryCache,
                postDetailCache,
                postCounterCache,
                postHotnessDomainService,
                policyProperties,
                AllowAllProjectionGuard.INSTANCE
        );
    }

    private static PostHotFeedProjectionApplicationService newService(
            PostContentRepository postContentRepository,
            LikeQueryPort likeQueryPort,
            PostFeedCache postFeedCache,
            PostSummaryCache postSummaryCache,
            PostDetailCache postDetailCache,
            PostCounterCache postCounterCache,
            PostHotnessDomainService postHotnessDomainService,
            ContentFeedPolicyProperties policyProperties,
            HotFeedProjectionGuard projectionGuard
    ) {
        return newService(
                postContentRepository,
                likeQueryPort,
                postFeedCache,
                postSummaryCache,
                postDetailCache,
                postCounterCache,
                postHotnessDomainService,
                policyProperties,
                projectionGuard,
                ImmediateProjectionCompletion.INSTANCE
        );
    }

    private static PostHotFeedProjectionApplicationService newService(
            PostContentRepository postContentRepository,
            LikeQueryPort likeQueryPort,
            PostFeedCache postFeedCache,
            PostSummaryCache postSummaryCache,
            PostDetailCache postDetailCache,
            PostCounterCache postCounterCache,
            PostHotnessDomainService postHotnessDomainService,
            ContentFeedPolicyProperties policyProperties,
            HotFeedProjectionGuard projectionGuard,
            HotFeedProjectionCompletion projectionCompletion
    ) {
        return newService(
                postContentRepository,
                likeQueryPort,
                postFeedCache,
                postSummaryCache,
                postDetailCache,
                postCounterCache,
                postHotnessDomainService,
                policyProperties,
                projectionGuard,
                new PostHotFeedProjectionTransactionOperations(postContentRepository),
                projectionCompletion
        );
    }

    private static PostHotFeedProjectionApplicationService newService(
            PostContentRepository postContentRepository,
            LikeQueryPort likeQueryPort,
            PostFeedCache postFeedCache,
            PostSummaryCache postSummaryCache,
            PostDetailCache postDetailCache,
            PostCounterCache postCounterCache,
            PostHotnessDomainService postHotnessDomainService,
            ContentFeedPolicyProperties policyProperties,
            HotFeedProjectionGuard projectionGuard,
            PostHotFeedProjectionTransactionOperations transactionOperations,
            HotFeedProjectionCompletion projectionCompletion
    ) {
        return new PostHotFeedProjectionApplicationService(
                postContentRepository,
                likeQueryPort,
                postFeedCache,
                postSummaryCache,
                postDetailCache,
                postCounterCache,
                postHotnessDomainService,
                policyProperties,
                projectionGuard,
                transactionOperations,
                projectionCompletion
        );
    }

    private enum ImmediateProjectionCompletion implements HotFeedProjectionCompletion {
        INSTANCE;

        @Override
        public void afterTransaction(Runnable committedAction, Runnable rolledBackAction) {
            committedAction.run();
        }
    }

    private static void verifyGlobalProjection(
            PostFeedCache cache,
            UUID postId,
            int type,
            double score
    ) {
        verify(cache).upsertGlobalHot(
                argThat(entry -> projectionMatches(entry, postId, type, score)),
                eq("hot-v2"),
                eq(7L),
                eq(8L)
        );
    }

    private static void verifyBoardProjection(
            PostFeedCache cache,
            UUID boardId,
            UUID postId,
            int type,
            double score
    ) {
        verify(cache).upsertBoardHot(
                eq(boardId),
                argThat(entry -> projectionMatches(entry, postId, type, score)),
                eq("hot-v2"),
                eq(7L),
                eq(8L)
        );
    }

    private static boolean projectionMatches(
            PostFeedCache.HotProjectionEntry entry,
            UUID postId,
            int type,
            double score
    ) {
        return entry != null
                && postId.equals(entry.postId())
                && entry.type() == type
                && Double.compare(entry.score(), score) == 0
                && entry.createTime() != null;
    }

    private enum AllowAllProjectionGuard implements HotFeedProjectionGuard {
        INSTANCE;

        @Override
        public ProjectionAttempt tryBegin(
                UUID postId,
                String sourceEventId,
                long sourceVersion,
                PostProjectionVersionLane sourceVersionLane,
                boolean terminalDeletion
        ) {
            return ProjectionAttempt.accepted(
                    postId,
                    sourceEventId,
                    sourceVersion,
                    sourceVersionLane,
                    terminalDeletion,
                    "test"
            );
        }

        @Override
        public boolean isCurrent(ProjectionAttempt attempt) {
            return true;
        }

        @Override
        public void commit(ProjectionAttempt attempt) {
        }

        @Override
        public void abort(ProjectionAttempt attempt) {
        }
    }

    private static DiscussPost post(UUID postId, UUID boardId, int status, double score) {
        DiscussPost post = new DiscussPost();
        post.setId(postId);
        post.setCategoryId(boardId);
        post.setStatus(status);
        post.setScore(score);
        post.setCommentCount(6);
        post.setCreateTime(new Date());
        post.setAggregateVersion(7L);
        return post;
    }
}
