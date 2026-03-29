package com.nowcoder.community.content.service;

import com.nowcoder.community.content.api.model.PostSummaryView;
import com.nowcoder.community.content.assembler.PostSummaryAssembler;
import com.nowcoder.community.content.entity.Comment;
import com.nowcoder.community.content.entity.DiscussPost;
import com.nowcoder.community.content.mapper.BookmarkMapper;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;
import java.util.Map;

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

        DiscussPost post = new DiscussPost();
        post.setId(11);
        Comment lastActivity = new Comment();
        lastActivity.setEntityId(11);
        lastActivity.setUserId(8);
        lastActivity.setCreateTime(new Date());
        PostSummaryView assembled = new PostSummaryView(
                11, 7, "title", 0, 0, new Date(), 3, 9.5, 2,
                List.of("spring"), 8, lastActivity.getCreateTime(), lastActivity.getCreateTime(), "latest reply"
        );

        when(bookmarkMapper.selectBookmarkedPosts(7, 0, 10)).thenReturn(List.of(post));
        when(commentService.getLatestPostActivitiesByPostIds(List.of(11))).thenReturn(Map.of(11, lastActivity));
        when(tagService.getTagsByPostIds(List.of(11))).thenReturn(Map.of(11, List.of("spring")));
        when(postSummaryAssembler.assemble(post, lastActivity, List.of("spring"))).thenReturn(assembled);

        BookmarkService service = new BookmarkService(bookmarkMapper, postService, commentService, tagService, postSummaryAssembler);

        List<PostSummaryView> summaries = service.listBookmarkedPostSummaries(7, 0, 10);

        assertThat(summaries).containsExactly(assembled);
        verify(postSummaryAssembler).assemble(post, lastActivity, List.of("spring"));
    }
}
