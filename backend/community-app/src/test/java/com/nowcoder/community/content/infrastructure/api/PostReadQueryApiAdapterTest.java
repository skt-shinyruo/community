package com.nowcoder.community.content.infrastructure.api;

import com.nowcoder.community.content.api.query.PostReadQueryApi.PostSummaryView;
import com.nowcoder.community.content.api.query.PostReadQueryApi.RecentUserCommentView;
import com.nowcoder.community.content.application.PostReadApplicationService;
import com.nowcoder.community.content.application.result.PostSummaryResult;
import com.nowcoder.community.content.application.result.RecentUserCommentResult;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PostReadQueryApiAdapterTest {

    private final PostReadApplicationService delegate = mock(PostReadApplicationService.class);
    private final PostReadQueryApiAdapter adapter = new PostReadQueryApiAdapter(delegate);

    @Test
    void shouldPublishOnlyAuthorPostSummaryFields() {
        UUID userId = uuid(1);
        UUID postId = uuid(2);
        UUID categoryId = uuid(3);
        UUID lastReplyUserId = uuid(4);
        Date createdAt = new Date(1_000L);
        Date lastReplyAt = new Date(2_000L);
        Date lastActivityAt = new Date(3_000L);
        when(delegate.listPostsByUser(userId, 0, 5)).thenReturn(List.of(new PostSummaryResult(
                postId,
                userId,
                "title",
                "internal preview",
                1,
                0,
                createdAt,
                7,
                9.5,
                categoryId,
                List.of("java"),
                lastReplyUserId,
                lastReplyAt,
                lastActivityAt,
                "last reply"
        )));

        assertThat(adapter.listPostsByUser(userId, 0, 5)).containsExactly(new PostSummaryView(
                postId,
                userId,
                "title",
                1,
                0,
                createdAt,
                7,
                9.5,
                categoryId,
                List.of("java"),
                lastReplyUserId,
                lastReplyAt,
                lastActivityAt,
                "last reply"
        ));
    }

    @Test
    void shouldPublishRecentCommentFields() {
        UUID userId = uuid(11);
        UUID commentId = uuid(12);
        UUID entityId = uuid(13);
        UUID targetId = uuid(14);
        UUID postId = uuid(15);
        Date createdAt = new Date(4_000L);
        when(delegate.listRecentCommentsByUser(userId, 1, 10)).thenReturn(List.of(new RecentUserCommentResult(
                commentId,
                userId,
                1,
                entityId,
                targetId,
                postId,
                "post title",
                "comment",
                createdAt
        )));

        assertThat(adapter.listRecentCommentsByUser(userId, 1, 10)).containsExactly(new RecentUserCommentView(
                commentId,
                userId,
                1,
                entityId,
                targetId,
                postId,
                "post title",
                "comment",
                createdAt
        ));
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }
}
