package com.nowcoder.community.content.infrastructure.persistence;

import com.nowcoder.community.content.application.result.PostSummaryResult;
import com.nowcoder.community.content.application.assembler.PostSummaryAssembler;
import com.nowcoder.community.content.domain.model.Comment;
import com.nowcoder.community.content.domain.model.DiscussPost;
import com.nowcoder.community.content.infrastructure.persistence.mapper.BookmarkMapper;
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

class BookmarkServiceTest {

    @Test
    void listBookmarkedPostSummariesShouldAssembleViewsWithActivityAndTags() {
        BookmarkMapper bookmarkMapper = mock(BookmarkMapper.class);
        PostService postService = mock(PostService.class);
        CommentService commentService = mock(CommentService.class);
        TagService tagService = mock(TagService.class);
        PostSummaryAssembler postSummaryAssembler = mock(PostSummaryAssembler.class);
        UUID userId = uuid(7);
        UUID postId = uuid(11);
        UUID categoryId = uuid(2);
        UUID lastReplyUserId = uuid(8);

        DiscussPost post = new DiscussPost();
        post.setId(postId);
        Comment lastActivity = new Comment();
        lastActivity.setEntityId(postId);
        lastActivity.setUserId(lastReplyUserId);
        lastActivity.setCreateTime(new Date());
        PostSummaryResult assembled = new PostSummaryResult(
                postId, userId, "title", 0, 0, new Date(), 3, 9.5, categoryId,
                List.of("spring"), lastReplyUserId, lastActivity.getCreateTime(), lastActivity.getCreateTime(), "latest reply"
        );

        when(bookmarkMapper.selectBookmarkedPosts(userId, 0, 10)).thenReturn(List.of(post));
        when(commentService.getLatestPostActivitiesByPostIds(List.of(postId))).thenReturn(Map.of(postId, lastActivity));
        when(tagService.getTagsByPostIds(List.of(postId))).thenReturn(Map.of(postId, List.of("spring")));
        when(postSummaryAssembler.assemble(post, lastActivity, List.of("spring"))).thenReturn(assembled);

        BookmarkService service = new BookmarkService(bookmarkMapper, postService, commentService, tagService, postSummaryAssembler);

        List<PostSummaryResult> summaries = service.listBookmarkedPostSummaries(userId, 0, 10);

        assertThat(summaries).containsExactly(assembled);
        verify(postSummaryAssembler).assemble(post, lastActivity, List.of("spring"));
    }
}
