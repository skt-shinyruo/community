package com.nowcoder.community.content.application;

import com.nowcoder.community.content.application.command.ProjectPostHotFeedCommand;
import com.nowcoder.community.content.domain.model.DiscussPost;
import com.nowcoder.community.content.domain.repository.PostContentRepository;
import com.nowcoder.community.content.domain.service.PostHotnessDomainService;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.mockito.ArgumentMatchers.eq;
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
        PostHotFeedProjectionApplicationService service = new PostHotFeedProjectionApplicationService(
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
        when(postHotnessDomainService.recomputeScore(post, 41L, 1.0)).thenReturn(88.5);

        service.project(new ProjectPostHotFeedCommand(
                uuid(200),
                uuid(10),
                1.0,
                "evt-post-updated",
                42L
        ));

        verify(postFeedCache).writeRankVersion("hot-v2");
        verify(postFeedCache).upsertGlobalHot(uuid(200), 88.5, "hot-v2");
        verify(postFeedCache).upsertBoardHot(uuid(10), uuid(200), 88.5, "hot-v2");
        verify(postCounterCache).updateScore(uuid(200), 88.5);
        verify(postSummaryCache).evictAll(List.of(uuid(200)));
        verify(postDetailCache).evict(uuid(200));
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
        PostHotFeedProjectionApplicationService service = new PostHotFeedProjectionApplicationService(
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
                48L
        );

        when(projectionGuard.tryBegin(uuid(206), "evt-duplicate", 48L)).thenReturn(attempt);

        service.project(new ProjectPostHotFeedCommand(
                uuid(206),
                uuid(16),
                1.0,
                "evt-duplicate",
                48L
        ));

        verify(projectionGuard).tryBegin(uuid(206), "evt-duplicate", 48L);
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
        PostHotFeedProjectionApplicationService service = new PostHotFeedProjectionApplicationService(
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
                47L
        );

        when(projectionGuard.tryBegin(uuid(207), "evt-stale", 47L)).thenReturn(attempt);

        service.project(new ProjectPostHotFeedCommand(
                uuid(207),
                uuid(17),
                1.0,
                "evt-stale",
                47L
        ));

        verify(projectionGuard).tryBegin(uuid(207), "evt-stale", 47L);
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
        PostHotFeedProjectionApplicationService service = new PostHotFeedProjectionApplicationService(
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
                "token-old"
        );
        DiscussPost post = post(uuid(210), uuid(20), 0, 10.0);
        when(projectionGuard.tryBegin(uuid(210), "evt-old", 50L)).thenReturn(attempt);
        when(postContentRepository.getByIdAllowDeleted(uuid(210))).thenReturn(post);
        when(likeQueryPort.countPostLikes(uuid(210))).thenReturn(1L);
        when(postHotnessDomainService.recomputeScore(post, 1L, 1.0)).thenReturn(12.0);
        when(projectionGuard.isCurrent(attempt)).thenReturn(false);

        service.project(new ProjectPostHotFeedCommand(
                uuid(210),
                uuid(20),
                1.0,
                "evt-old",
                50L
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
        PostHotFeedProjectionApplicationService service = new PostHotFeedProjectionApplicationService(
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
                "token-superseded"
        );
        DiscussPost post = post(uuid(212), uuid(22), 0, 10.0);
        when(projectionGuard.tryBegin(uuid(212), "evt-superseded", 52L)).thenReturn(attempt);
        when(postContentRepository.getByIdAllowDeleted(uuid(212))).thenReturn(post);
        when(likeQueryPort.countPostLikes(uuid(212))).thenReturn(2L);
        when(postHotnessDomainService.recomputeScore(post, 2L, 1.0)).thenReturn(14.0);
        when(projectionGuard.isCurrent(attempt)).thenReturn(true, false);

        service.project(new ProjectPostHotFeedCommand(
                uuid(212),
                uuid(22),
                1.0,
                "evt-superseded",
                52L
        ));

        verify(projectionGuard).abort(attempt);
        verifyNoInteractions(postFeedCache, postSummaryCache, postDetailCache, postCounterCache);
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
        PostHotFeedProjectionApplicationService service = new PostHotFeedProjectionApplicationService(
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
                "token-current"
        );
        DiscussPost post = post(uuid(211), uuid(21), 0, 10.0);
        when(projectionGuard.tryBegin(uuid(211), "evt-current", 51L)).thenReturn(attempt);
        when(postContentRepository.getByIdAllowDeleted(uuid(211))).thenReturn(post);
        when(likeQueryPort.countPostLikes(uuid(211))).thenReturn(2L);
        when(postHotnessDomainService.recomputeScore(post, 2L, 1.0)).thenReturn(14.0);
        when(projectionGuard.isCurrent(attempt)).thenReturn(true);

        service.project(new ProjectPostHotFeedCommand(
                uuid(211),
                uuid(21),
                1.0,
                "evt-current",
                51L
        ));

        verify(postFeedCache).upsertGlobalHot(uuid(211), 14.0, "hot-v2");
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
        PostHotFeedProjectionApplicationService service = new PostHotFeedProjectionApplicationService(
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
                "token-new"
        );
        HotFeedProjectionGuard.ProjectionAttempt stale = HotFeedProjectionGuard.ProjectionAttempt.rejected(
                uuid(230),
                "evt-old",
                10L
        );
        DiscussPost post = post(uuid(230), uuid(30), 0, 10.0);
        when(projectionGuard.tryBegin(uuid(230), "evt-new", 20L)).thenReturn(accepted);
        when(projectionGuard.tryBegin(uuid(230), "evt-old", 10L)).thenReturn(stale);
        when(postContentRepository.getByIdAllowDeleted(uuid(230))).thenReturn(post);
        when(likeQueryPort.countPostLikes(uuid(230))).thenReturn(2L);
        when(postHotnessDomainService.recomputeScore(post, 2L, 1.0)).thenReturn(14.0);
        when(projectionGuard.isCurrent(accepted)).thenReturn(true);

        service.project(new ProjectPostHotFeedCommand(uuid(230), uuid(30), 1.0, "evt-new", 20L));
        service.project(new ProjectPostHotFeedCommand(uuid(230), uuid(30), 1.0, "evt-old", 10L));

        verify(postFeedCache).upsertGlobalHot(uuid(230), 14.0, "hot-v2");
        verify(postHotnessDomainService, times(1)).recomputeScore(post, 2L, 1.0);
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
        PostHotFeedProjectionApplicationService service = new PostHotFeedProjectionApplicationService(
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

        service.project(new ProjectPostHotFeedCommand(uuid(208), uuid(18), 1.0, " ", 49L));

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
        PostHotFeedProjectionApplicationService service = new PostHotFeedProjectionApplicationService(
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

        service.project(new ProjectPostHotFeedCommand(uuid(209), uuid(19), 1.0, "evt-invalid-version", 0L));

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
        PostHotFeedProjectionApplicationService service = new PostHotFeedProjectionApplicationService(
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
        when(postHotnessDomainService.recomputeScore(post, 5L, 1.0)).thenReturn(18.0);

        service.project(new ProjectPostHotFeedCommand(
                uuid(203),
                uuid(13),
                1.0,
                "evt-post-updated",
                43L
        ));

        verify(postFeedCache).remove(uuid(203), null);
        verify(postFeedCache).upsertBoardHot(uuid(13), uuid(203), 18.0, "hot-v2");
    }

    @Test
    void deletedPostShouldRemoveFromBothFeedsAndEvictCaches() {
        PostContentRepository postContentRepository = mock(PostContentRepository.class);
        LikeQueryPort likeQueryPort = mock(LikeQueryPort.class);
        PostFeedCache postFeedCache = mock(PostFeedCache.class);
        PostSummaryCache postSummaryCache = mock(PostSummaryCache.class);
        PostDetailCache postDetailCache = mock(PostDetailCache.class);
        PostCounterCache postCounterCache = mock(PostCounterCache.class);
        PostHotnessDomainService postHotnessDomainService = mock(PostHotnessDomainService.class);
        PostHotFeedProjectionApplicationService service = new PostHotFeedProjectionApplicationService(
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
                0.0,
                "evt-post-deleted",
                44L
        ));

        verify(postFeedCache).writeRankVersion("hot-v2");
        verify(postFeedCache).remove(uuid(201), null);
        verify(postSummaryCache).evictAll(List.of(uuid(201)));
        verify(postDetailCache).evict(uuid(201));
        verifyNoInteractions(likeQueryPort, postHotnessDomainService);
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
        PostHotFeedProjectionApplicationService service = new PostHotFeedProjectionApplicationService(
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
        when(postHotnessDomainService.recomputeScore(post, 9L, 1.0)).thenReturn(31.0);

        service.project(new ProjectPostHotFeedCommand(
                uuid(202),
                null,
                1.0,
                "evt-like-created",
                45L
        ));

        verify(postFeedCache).upsertGlobalHot(uuid(202), 31.0, "hot-v2");
        verify(postFeedCache).upsertBoardHot(eq(uuid(12)), eq(uuid(202)), eq(31.0), eq("hot-v2"));
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
        PostHotFeedProjectionApplicationService service = new PostHotFeedProjectionApplicationService(
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
        when(postHotnessDomainService.recomputeScore(post, 7L, 1.0)).thenReturn(25.0);

        service.project(new ProjectPostHotFeedCommand(
                uuid(204),
                uuid(99),
                1.0,
                "evt-post-updated",
                46L
        ));

        verify(postFeedCache).remove(uuid(204), null);
        verify(postFeedCache).upsertBoardHot(eq(uuid(14)), eq(uuid(204)), eq(25.0), eq("hot-v2"));
    }

    @Test
    void hiddenPostShouldPersistRankVersionAndRemoveFromFeeds() {
        PostContentRepository postContentRepository = mock(PostContentRepository.class);
        LikeQueryPort likeQueryPort = mock(LikeQueryPort.class);
        PostFeedCache postFeedCache = mock(PostFeedCache.class);
        PostSummaryCache postSummaryCache = mock(PostSummaryCache.class);
        PostDetailCache postDetailCache = mock(PostDetailCache.class);
        PostCounterCache postCounterCache = mock(PostCounterCache.class);
        PostHotnessDomainService postHotnessDomainService = mock(PostHotnessDomainService.class);
        PostHotFeedProjectionApplicationService service = new PostHotFeedProjectionApplicationService(
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

        service.project(new ProjectPostHotFeedCommand(
                uuid(205),
                uuid(15),
                1.5,
                "ce:1",
                47L
        ));

        verify(postFeedCache).writeRankVersion("hot-v2");
        verify(postFeedCache).remove(uuid(205), null);
        verifyNoInteractions(likeQueryPort, postHotnessDomainService, postCounterCache);
    }

    private static ContentFeedPolicyProperties policyProperties() {
        ContentFeedPolicyProperties properties = new ContentFeedPolicyProperties();
        properties.setHotRankVersion("hot-v2");
        return properties;
    }

    private static DiscussPost post(UUID postId, UUID boardId, int status, double score) {
        DiscussPost post = new DiscussPost();
        post.setId(postId);
        post.setCategoryId(boardId);
        post.setStatus(status);
        post.setScore(score);
        post.setCommentCount(6);
        post.setCreateTime(new Date());
        return post;
    }
}
