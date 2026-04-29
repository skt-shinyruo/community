package com.nowcoder.community.content.controller;

import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.content.application.result.CommentCreateResult;
import com.nowcoder.community.content.application.result.CommentResult;
import com.nowcoder.community.content.application.result.PostDetailResult;
import com.nowcoder.community.content.application.result.PostSummaryResult;
import com.nowcoder.community.content.application.PostPublishingApplicationService;
import com.nowcoder.community.content.application.PostModerationApplicationService;
import com.nowcoder.community.content.application.result.PostCreateResult;
import com.nowcoder.community.content.controller.dto.BatchPostSummaryRequest;
import com.nowcoder.community.content.controller.dto.CommentResponse;
import com.nowcoder.community.content.controller.dto.CreateCommentRequest;
import com.nowcoder.community.content.controller.dto.CreatePostRequest;
import com.nowcoder.community.content.controller.dto.CreatePostResponse;
import com.nowcoder.community.content.controller.dto.PostDetailResponse;
import com.nowcoder.community.content.controller.dto.PostSummaryResponse;
import com.nowcoder.community.content.controller.dto.UpdateCommentRequest;
import com.nowcoder.community.content.controller.dto.UpdatePostRequest;
import com.nowcoder.community.content.application.CommentApplicationService;
import com.nowcoder.community.content.application.CommentReadApplicationService;
import com.nowcoder.community.content.application.PostReadApplicationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static com.nowcoder.community.common.idempotency.IdempotencyGuard.HEADER_IDEMPOTENCY_KEY;
import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostControllerUnitTest {

    @Mock
    private PostReadApplicationService postReadApplicationService;

    @Mock
    private CommentReadApplicationService commentReadApplicationService;

    @Mock
    private PostPublishingApplicationService postPublishingApplicationService;

    @Mock
    private PostModerationApplicationService postModerationApplicationService;

    @Mock
    private CommentApplicationService commentApplicationService;

    private PostController controller;

    @BeforeEach
    void setUp() {
        controller = new PostController(
                postReadApplicationService,
                commentReadApplicationService,
                postPublishingApplicationService,
                postModerationApplicationService,
                commentApplicationService
        );
    }

    @Test
    void createShouldUseSameDomainPublishingApplicationService() {
        UUID userId = uuid(7);
        UUID postId = uuid(11);
        UUID categoryId = uuid(3);
        PostCreateResult createResult = new PostCreateResult(postId);
        CreatePostRequest request = new CreatePostRequest();
        request.setTitle("title");
        request.setContent("content");
        request.setCategoryId(categoryId);
        request.setTags(List.of("java"));
        when(postPublishingApplicationService.create(userId, "idem-1", "title", "content", categoryId, List.of("java")))
                .thenReturn(createResult);

        Result<CreatePostResponse> result = controller.create(authentication(userId), "idem-1", request);

        assertThat(result.getCode()).isEqualTo(0);
        assertThat(result.getData().getPostId()).isEqualTo(postId);
        verify(postPublishingApplicationService).create(userId, "idem-1", "title", "content", categoryId, List.of("java"));
    }

    @Test
    void listAndBatchSummaryShouldReturnDtoResponsesFromControllerMapper() {
        UUID userId = uuid(7);
        UUID postId = uuid(11);
        UUID secondPostId = uuid(12);
        UUID categoryId = uuid(3);
        Date createTime = new Date();
        PostSummaryResult firstView = postSummaryView(postId, userId, categoryId, createTime, "first");
        PostSummaryResult secondView = postSummaryView(secondPostId, userId, categoryId, createTime, "second");
        BatchPostSummaryRequest request = new BatchPostSummaryRequest();
        request.setPostIds(List.of(postId, secondPostId));
        when(postReadApplicationService.listPosts(userId, "latest", categoryId, "java", false, 0, 10))
                .thenReturn(List.of(firstView));
        when(postReadApplicationService.listPostsByIds(List.of(postId, secondPostId)))
                .thenReturn(List.of(firstView, secondView));

        Result<List<PostSummaryResponse>> listResult = controller.list(authentication(userId), "latest", categoryId, "java", false, 0, 10);
        Result<List<PostSummaryResponse>> batchResult = controller.batchSummary(request);

        assertThat(listResult.getData()).singleElement().satisfies(response -> {
            assertThat(response.getId()).isEqualTo(postId);
            assertThat(response.getTitle()).isEqualTo("first");
        });
        assertThat(batchResult.getData()).extracting(PostSummaryResponse::getTitle).containsExactly("first", "second");
        verify(postReadApplicationService).listPosts(userId, "latest", categoryId, "java", false, 0, 10);
        verify(postReadApplicationService).listPostsByIds(List.of(postId, secondPostId));
    }

    @Test
    void detailCommentsAndRepliesShouldUseApplicationServicesReturningOwnerViews() {
        UUID actorUserId = uuid(7);
        UUID postId = uuid(11);
        UUID commentId = uuid(21);
        UUID categoryId = uuid(3);
        PostDetailResult detailView = new PostDetailResult(
                postId,
                actorUserId,
                "detail",
                "body",
                0,
                0,
                new Date(),
                null,
                0,
                1,
                10.0,
                categoryId,
                List.of("java"),
                3L,
                false,
                false
        );
        CommentResult commentView = new CommentResult(commentId, actorUserId, 1, postId, null, "comment", new Date(), null, 0);
        when(postReadApplicationService.getPostDetail(actorUserId, postId)).thenReturn(detailView);
        when(commentReadApplicationService.comments(postId, 0, 10)).thenReturn(List.of(commentView));
        when(commentReadApplicationService.replies(postId, commentId, 0, 10)).thenReturn(List.of(commentView));

        Result<PostDetailResponse> detailResult = controller.detail(authentication(actorUserId), postId);
        Result<List<CommentResponse>> commentsResult = controller.comments(postId, 0, 10);
        Result<List<CommentResponse>> repliesResult = controller.replies(postId, commentId, 0, 10);

        assertThat(detailResult.getData().getId()).isEqualTo(postId);
        assertThat(detailResult.getData().getTitle()).isEqualTo("detail");
        assertThat(commentsResult.getData()).singleElement().satisfies(response -> {
            assertThat(response.getId()).isEqualTo(commentId);
            assertThat(response.getContent()).isEqualTo("comment");
        });
        assertThat(repliesResult.getData()).singleElement().satisfies(response -> {
            assertThat(response.getId()).isEqualTo(commentId);
            assertThat(response.getContent()).isEqualTo("comment");
        });
        verify(postReadApplicationService).getPostDetail(actorUserId, postId);
        verify(commentReadApplicationService).comments(postId, 0, 10);
        verify(commentReadApplicationService).replies(postId, commentId, 0, 10);
    }

    @Test
    void writeAndModerationEndpointsShouldDelegateToSameDomainApplicationServices() {
        UUID userId = uuid(7);
        UUID postId = uuid(11);
        UUID commentId = uuid(21);
        UUID createdCommentId = uuid(22);
        UUID categoryId = uuid(3);
        CreateCommentRequest createCommentRequest = new CreateCommentRequest();
        createCommentRequest.setEntityType(1);
        createCommentRequest.setEntityId(postId);
        createCommentRequest.setTargetId(uuid(31));
        createCommentRequest.setContent("reply");
        UpdatePostRequest updatePostRequest = new UpdatePostRequest();
        updatePostRequest.setTitle("updated");
        updatePostRequest.setContent("body");
        updatePostRequest.setCategoryId(categoryId);
        updatePostRequest.setTags(List.of("spring"));
        UpdateCommentRequest updateCommentRequest = new UpdateCommentRequest();
        updateCommentRequest.setContent("edited");
        when(commentApplicationService.create(userId, "idem-2", postId, 1, postId, createCommentRequest.getTargetId(), "reply"))
                .thenReturn(new CommentCreateResult(createdCommentId));

        Result<UUID> addCommentResult = controller.addComment(authentication(userId), "idem-2", postId, createCommentRequest);
        Result<Void> updatePostResult = controller.updatePost(authentication(userId), postId, updatePostRequest);
        Result<Void> deleteOwnPostResult = controller.deleteByAuthor(authentication(userId), postId);
        Result<Void> updateCommentResult = controller.updateComment(authentication(userId), postId, commentId, updateCommentRequest);
        Result<Void> topResult = controller.top(authentication(userId), postId);
        Result<Void> wonderfulResult = controller.wonderful(authentication(userId), postId);
        Result<Void> deleteResult = controller.delete(authentication(userId), postId);

        assertThat(addCommentResult.getData()).isEqualTo(createdCommentId);
        assertThat(updatePostResult.getCode()).isEqualTo(0);
        assertThat(deleteOwnPostResult.getCode()).isEqualTo(0);
        assertThat(updateCommentResult.getCode()).isEqualTo(0);
        assertThat(topResult.getCode()).isEqualTo(0);
        assertThat(wonderfulResult.getCode()).isEqualTo(0);
        assertThat(deleteResult.getCode()).isEqualTo(0);
        verify(commentApplicationService).create(userId, "idem-2", postId, 1, postId, createCommentRequest.getTargetId(), "reply");
        verify(postPublishingApplicationService).updatePost(userId, postId, "updated", "body", categoryId, List.of("spring"));
        verify(postPublishingApplicationService).deleteByAuthor(userId, postId);
        verify(commentApplicationService).updateComment(userId, postId, commentId, "edited");
        verify(postModerationApplicationService).top(userId, postId);
        verify(postModerationApplicationService).wonderful(userId, postId);
        verify(postModerationApplicationService).delete(userId, postId);
    }

    private static PostSummaryResult postSummaryView(UUID postId, UUID userId, UUID categoryId, Date createTime, String title) {
        return new PostSummaryResult(
                postId,
                userId,
                title,
                0,
                0,
                createTime,
                1,
                10.0,
                categoryId,
                List.of("java"),
                null,
                null,
                createTime,
                null
        );
    }

    private static Authentication authentication(UUID userId) {
        Jwt jwt = Jwt.withTokenValue("token-" + userId)
                .header("alg", "none")
                .subject(userId.toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        return new TestingAuthenticationToken(jwt, null);
    }
}
