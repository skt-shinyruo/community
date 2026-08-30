package com.nowcoder.community.content.application;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.content.api.model.HotFeedCachePrewarmRequest;
import com.nowcoder.community.content.api.model.UpdateHotFeedDegradationSignalRequest;
import com.nowcoder.community.content.api.model.HotFeedDegradationSignal;
import com.nowcoder.community.content.application.result.PostSummaryResult;
import com.nowcoder.community.content.domain.model.DiscussPost;
import com.nowcoder.community.content.domain.repository.PostContentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HotFeedCacheGovernanceApplicationServiceTest {

    private PostFeedCache postFeedCache;
    private PostContentRepository postContentRepository;
    private PostFeedSummaryLoader postFeedSummaryLoader;
    private ContentFeedPolicyProperties policyProperties;
    private HotFeedCacheGovernanceApplicationService service;

    @BeforeEach
    void setUp() {
        postFeedCache = mock(PostFeedCache.class);
        postContentRepository = mock(PostContentRepository.class);
        postFeedSummaryLoader = mock(PostFeedSummaryLoader.class);
        policyProperties = new ContentFeedPolicyProperties();
        service = new HotFeedCacheGovernanceApplicationService(
                postFeedCache,
                postContentRepository,
                postFeedSummaryLoader,
                policyProperties,
                Clock.systemUTC()
        );
    }

    @Test
    void getStatusShouldReadGlobalCacheState() {
        Instant prewarmAt = Instant.parse("2026-07-07T10:00:00Z");
        when(postFeedCache.readRankVersion()).thenReturn("hot-v9");
        when(postFeedCache.countGlobalHot()).thenReturn(12L);
        when(postFeedCache.readLastPrewarmAt("global", null)).thenReturn(prewarmAt);
        when(postFeedCache.readDegradationSignal()).thenReturn(new PostFeedCache.DegradationSignal(false, "", null));

        var result = service.getStatus("global", null);

        assertThat(result.scope()).isEqualTo("global");
        assertThat(result.boardId()).isNull();
        assertThat(result.rankVersion()).isEqualTo("hot-v9");
        assertThat(result.itemCount()).isEqualTo(12L);
        assertThat(result.summaryCacheAvailable()).isTrue();
        assertThat(result.degraded()).isFalse();
        assertThat(result.lastPrewarmAt()).isEqualTo(prewarmAt);
    }

    @Test
    void boardStatusShouldRequireBoardId() {
        assertThatThrownBy(() -> service.getStatus("board", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("boardId is required for board scope");
    }

    @Test
    void prewarmGlobalShouldWriteRankVersionHotEntriesAndSummaries() {
        UUID firstPostId = uuid(1);
        UUID secondPostId = uuid(2);
        DiscussPost first = post(firstPostId, 91.0, uuid(11));
        DiscussPost second = post(secondPostId, 88.0, uuid(12));
        List<DiscussPost> posts = List.of(first, second);
        List<PostSummaryResult> summaries = List.of(summary(firstPostId), summary(secondPostId));
        when(postContentRepository.listPosts(0, 2, PostContentRepository.ORDER_HOT)).thenReturn(posts);
        when(postFeedSummaryLoader.prewarmCurrentPosts(posts)).thenReturn(summaries);

        var result = service.prewarm(new HotFeedCachePrewarmRequest("global", null, 2, "warm cold cache"));

        assertThat(result.scope()).isEqualTo("global");
        assertThat(result.requestedCount()).isEqualTo(2);
        assertThat(result.loadedCount()).isEqualTo(2);
        assertThat(result.warmedCount()).isEqualTo(2);
        assertThat(result.rankVersion()).isEqualTo("hot-v2");
        verify(postFeedCache).writeRankVersion("hot-v2");
        verify(postFeedCache).upsertGlobalHot(projectionOf(first), "hot-v2", 7L, 3L);
        verify(postFeedCache).upsertGlobalHot(projectionOf(second), "hot-v2", 7L, 3L);
        verify(postFeedSummaryLoader).prewarmCurrentPosts(posts);
        verify(postFeedCache).writeLastPrewarmAt(org.mockito.ArgumentMatchers.eq("global"), org.mockito.ArgumentMatchers.isNull(), any(Instant.class));
    }

    @Test
    void prewarmBoardShouldWriteBoardHotEntries() {
        UUID boardId = uuid(9);
        UUID postId = uuid(3);
        DiscussPost post = post(postId, 77.0, boardId);
        List<DiscussPost> posts = List.of(post);
        List<PostSummaryResult> summaries = List.of(summary(postId));
        when(postContentRepository.listPosts(0, 10, PostContentRepository.ORDER_HOT, boardId, null)).thenReturn(posts);
        when(postFeedSummaryLoader.prewarmCurrentPosts(posts)).thenReturn(summaries);

        var result = service.prewarm(new HotFeedCachePrewarmRequest("board", boardId, 10, "warm board"));

        assertThat(result.scope()).isEqualTo("board");
        assertThat(result.boardId()).isEqualTo(boardId);
        assertThat(result.loadedCount()).isEqualTo(1);
        verify(postFeedCache).upsertBoardHot(boardId, projectionOf(post), "hot-v2", 7L, 3L);
        verify(postFeedSummaryLoader).prewarmCurrentPosts(posts);
    }

    @Test
    void degradationSignalShouldBeSetAndCleared() {
        when(postFeedCache.writeDegradationSignal(true, "redis maintenance"))
                .thenReturn(new PostFeedCache.DegradationSignal(true, "redis maintenance", Instant.parse("2026-07-07T10:00:00Z")));
        when(postFeedCache.writeDegradationSignal(false, ""))
                .thenReturn(new PostFeedCache.DegradationSignal(false, "", Instant.parse("2026-07-07T10:01:00Z")));

        var degraded = service.updateDegradationSignal(new UpdateHotFeedDegradationSignalRequest(true, "redis maintenance"));
        var cleared = service.updateDegradationSignal(new UpdateHotFeedDegradationSignalRequest(false, "clear"));

        assertThat(degraded.degraded()).isTrue();
        assertThat(degraded.reason()).isEqualTo("redis maintenance");
        assertThat(cleared.degraded()).isFalse();
        verify(postFeedCache).writeDegradationSignal(true, "redis maintenance");
        verify(postFeedCache).writeDegradationSignal(false, "");
    }

    @Test
    void prewarmShouldRejectInvalidLimit() {
        assertThatThrownBy(() -> service.prewarm(new HotFeedCachePrewarmRequest("global", null, 501, "warm")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("limit must be between 1 and 500");
    }

    private static DiscussPost post(UUID id, double score, UUID boardId) {
        DiscussPost post = new DiscussPost();
        post.setId(id);
        post.setUserId(uuid(99));
        post.setTitle("post-" + id);
        post.setScore(score);
        post.setCategoryId(boardId);
        post.setCreateTime(new Date(1_000));
        post.setAggregateVersion(7L);
        post.setScoreVersion(3L);
        return post;
    }

    private static PostFeedCache.HotProjectionEntry projectionOf(DiscussPost post) {
        return new PostFeedCache.HotProjectionEntry(
                post.getId(), post.getType(), post.getScore(), post.getCreateTime());
    }

    private static PostSummaryResult summary(UUID id) {
        return new PostSummaryResult(
                id,
                uuid(99),
                "post-" + id,
                "preview",
                0,
                0,
                new Date(1_000),
                0,
                0.0,
                uuid(9),
                List.of(),
                null,
                null,
                null,
                ""
        );
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }
}
