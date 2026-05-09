package com.nowcoder.community.content.application;

import com.nowcoder.community.content.application.result.PostSummaryResult;
import com.nowcoder.community.content.domain.model.Comment;
import com.nowcoder.community.content.domain.model.DiscussPost;
import com.nowcoder.community.content.domain.model.PostContentBlock;
import com.nowcoder.community.content.domain.repository.BookmarkRepository;
import com.nowcoder.community.content.domain.repository.CommentContentRepository;
import com.nowcoder.community.content.domain.repository.PostContentBlockRepository;
import com.nowcoder.community.content.domain.repository.TagContentRepository;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BookmarkApplicationServiceTest {

    @Test
    void listBookmarkedPostSummariesShouldAssembleViewsWithActivityAndTags() {
        BookmarkRepository bookmarkRepository = mock(BookmarkRepository.class);
        CommentContentRepository commentContentRepository = mock(CommentContentRepository.class);
        TagContentRepository tagContentRepository = mock(TagContentRepository.class);
        PostContentBlockRepository blockRepository = mock(PostContentBlockRepository.class);
        PostSummaryAssembler postSummaryAssembler = mock(PostSummaryAssembler.class);
        BookmarkApplicationService service = new BookmarkApplicationService(
                bookmarkRepository,
                commentContentRepository,
                tagContentRepository,
                blockRepository,
                new PostContentBlockTextProjector(),
                postSummaryAssembler
        );
        UUID userId = uuid(7);
        UUID postId = uuid(11);
        UUID categoryId = uuid(2);
        UUID lastReplyUserId = uuid(8);
        Date createTime = new Date();
        Date lastActivityTime = new Date(createTime.getTime() + 2_000);

        DiscussPost post = new DiscussPost();
        post.setId(postId);
        post.setUserId(userId);
        post.setCreateTime(createTime);
        post.setCategoryId(categoryId);
        Comment lastActivity = new Comment();
        lastActivity.setEntityId(postId);
        lastActivity.setUserId(lastReplyUserId);
        lastActivity.setCreateTime(lastActivityTime);

        PostSummaryResult view = new PostSummaryResult(
                postId,
                userId,
                "decoded title",
                "post preview",
                0,
                0,
                createTime,
                3,
                9.5,
                categoryId,
                List.of("spring"),
                lastReplyUserId,
                new Date(createTime.getTime() + 1_000),
                lastActivityTime,
                "latest reply"
        );
        when(bookmarkRepository.listBookmarkedPosts(userId, 0, 10)).thenReturn(List.of(post));
        when(commentContentRepository.getLatestPostActivitiesByPostIds(List.of(postId))).thenReturn(Map.of(postId, lastActivity));
        when(tagContentRepository.getTagsByPostIds(List.of(postId))).thenReturn(Map.of(postId, List.of("spring")));
        when(blockRepository.listByPostIds(List.of(postId))).thenReturn(Map.of(
                postId,
                List.of(new PostContentBlock(uuid(101), postId, 0, "paragraph", "post preview", null, "", "", "", null))
        ));
        when(postSummaryAssembler.assemble(post, lastActivity, List.of("spring"), "post preview")).thenReturn(view);

        List<PostSummaryResult> result = service.listBookmarkedPostSummaries(userId, 0, 10);

        assertThat(result).singleElement().satisfies(summary -> {
            assertThat(summary.id()).isEqualTo(postId);
            assertThat(summary.userId()).isEqualTo(userId);
            assertThat(summary.title()).isEqualTo("decoded title");
            assertThat(summary.tags()).containsExactly("spring");
            assertThat(summary.lastReplyPreview()).isEqualTo("latest reply");
            assertThat(summary.lastActivityTime()).isEqualTo(lastActivityTime);
        });
        verify(bookmarkRepository).listBookmarkedPosts(userId, 0, 10);
        verify(postSummaryAssembler).assemble(post, lastActivity, List.of("spring"), "post preview");
    }
}
