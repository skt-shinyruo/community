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
