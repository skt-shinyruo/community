package com.nowcoder.community.content.service;

import com.nowcoder.community.content.dto.CreatePostRequest;
import com.nowcoder.community.content.dto.CreatePostResponse;
import com.nowcoder.community.content.dto.PostSummaryResponse;
import com.nowcoder.community.content.config.ContentRenderProperties;
import com.nowcoder.community.content.entity.Comment;
import com.nowcoder.community.content.entity.DiscussPost;
import com.nowcoder.community.content.like.LikeQueryService;
import com.nowcoder.community.content.service.BookmarkService;
import com.nowcoder.community.content.service.CommentService;
import com.nowcoder.community.content.service.PostCommandService;
import com.nowcoder.community.content.service.PostService;
import com.nowcoder.community.content.service.SubscriptionService;
import com.nowcoder.community.content.service.TagService;
import com.nowcoder.community.content.text.ContentTextCodec;
import com.nowcoder.community.content.util.SensitiveFilter;
import com.nowcoder.community.infra.idempotency.IdempotencyGuard;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PostFacadeServiceTest {

    @Test
    void listShouldAssemblePostSummariesOutsideController() {
        PostService postService = mock(PostService.class);
        CommentService commentService = mock(CommentService.class);
        SensitiveFilter sensitiveFilter = mock(SensitiveFilter.class);
        LikeQueryService likeQueryService = mock(LikeQueryService.class);
        PostCommandService postCommandService = mock(PostCommandService.class);
        TagService tagService = mock(TagService.class);
        BookmarkService bookmarkService = mock(BookmarkService.class);
        SubscriptionService subscriptionService = mock(SubscriptionService.class);
        IdempotencyGuard idempotencyGuard = mock(IdempotencyGuard.class);
        ContentTextCodec textCodec = new ContentTextCodec(new ContentRenderProperties());

        DiscussPost post = new DiscussPost();
        post.setId(10);
        post.setUserId(2);
        post.setTitle("&lt;title&gt;");
        post.setContent("&lt;content&gt;");
        post.setCommentCount(3);

        Comment lastActivity = new Comment();
        lastActivity.setUserId(3);
        lastActivity.setCreateTime(new java.util.Date());

        when(postService.listPosts(0, 10, PostService.ORDER_LATEST, null, null)).thenReturn(List.of(post));
        when(commentService.getLatestPostActivitiesByPostIds(List.of(10))).thenReturn(Map.of(10, lastActivity));
        when(tagService.getTagsByPostIds(List.of(10))).thenReturn(Map.of(10, List.of("java")));

        PostFacadeService service = new PostFacadeService(
                postService,
                commentService,
                sensitiveFilter,
                likeQueryService,
                postCommandService,
                tagService,
                bookmarkService,
                subscriptionService,
                idempotencyGuard,
                textCodec
        );

        List<PostSummaryResponse> items = service.list(null, "latest", null, null, false, 0, 10);

        assertThat(items).hasSize(1);
        assertThat(items.get(0).getId()).isEqualTo(10);
        assertThat(items.get(0).getTitle()).isEqualTo("<title>");
        assertThat(items.get(0).getTags()).containsExactly("java");
        assertThat(items.get(0).getLastActivityTime()).isEqualTo(lastActivity.getCreateTime());
    }

    @Test
    void createShouldEscapeFilterAndDelegateCommand() {
        PostService postService = mock(PostService.class);
        CommentService commentService = mock(CommentService.class);
        SensitiveFilter sensitiveFilter = mock(SensitiveFilter.class);
        LikeQueryService likeQueryService = mock(LikeQueryService.class);
        PostCommandService postCommandService = mock(PostCommandService.class);
        TagService tagService = mock(TagService.class);
        BookmarkService bookmarkService = mock(BookmarkService.class);
        SubscriptionService subscriptionService = mock(SubscriptionService.class);
        IdempotencyGuard idempotencyGuard = mock(IdempotencyGuard.class);
        ContentTextCodec textCodec = new ContentTextCodec(new ContentRenderProperties());

        when(sensitiveFilter.filter("<title>")).thenReturn("title");
        when(sensitiveFilter.filter("<content>")).thenReturn("content");
        when(postCommandService.createPost(eq(7), eq("title"), eq("content"), eq(1), eq(List.of("java")))).thenReturn(99);
        when(idempotencyGuard.executeRequired(eq("content:create_post"), eq(7), eq("idem-1"), eq(CreatePostResponse.class), any()))
                .thenAnswer(invocation -> invocation.<Supplier<CreatePostResponse>>getArgument(4).get());

        PostFacadeService service = new PostFacadeService(
                postService,
                commentService,
                sensitiveFilter,
                likeQueryService,
                postCommandService,
                tagService,
                bookmarkService,
                subscriptionService,
                idempotencyGuard,
                textCodec
        );

        CreatePostRequest request = new CreatePostRequest();
        request.setTitle("<title>");
        request.setContent("<content>");
        request.setCategoryId(1);
        request.setTags(List.of("java"));

        CreatePostResponse response = service.create(7, "idem-1", request);

        assertThat(response.getPostId()).isEqualTo(99);
    }
}
