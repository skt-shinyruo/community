package com.nowcoder.community.infra.pagination;

import com.nowcoder.community.content.mapper.BookmarkMapper;
import com.nowcoder.community.content.mapper.CommentMapper;
import com.nowcoder.community.content.mapper.DiscussPostMapper;
import com.nowcoder.community.content.mapper.ModerationActionMapper;
import com.nowcoder.community.content.mapper.ReportMapper;
import com.nowcoder.community.content.service.BookmarkService;
import com.nowcoder.community.content.service.CommentService;
import com.nowcoder.community.content.service.ModerationService;
import com.nowcoder.community.content.service.PostService;
import com.nowcoder.community.content.service.ReportService;
import com.nowcoder.community.content.service.TagService;
import com.nowcoder.community.content.service.UserModerationGuard;
import com.nowcoder.community.content.config.ContentRenderProperties;
import com.nowcoder.community.content.event.ContentEventPublisher;
import com.nowcoder.community.content.score.PostScoreQueue;
import com.nowcoder.community.content.text.ContentTextCodec;
import com.nowcoder.community.content.util.SensitiveFilter;
import com.nowcoder.community.content.assembler.PostSummaryAssembler;
import com.nowcoder.community.message.mapper.MessageMapper;
import com.nowcoder.community.message.service.MessageItemAssembler;
import com.nowcoder.community.message.service.NoticeService;
import com.nowcoder.community.social.api.query.SocialBlockQueryApi;
import com.nowcoder.community.social.block.BlockService;
import com.nowcoder.community.social.event.SocialEventPublisher;
import com.nowcoder.community.social.follow.FollowRepository;
import com.nowcoder.community.social.follow.FollowService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static com.nowcoder.community.common.constants.EntityTypes.USER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaginationOffsetOverflowTest {

    @Test
    void postServiceShouldNotPassNegativeOffsetWhenPageIsHuge() {
        DiscussPostMapper discussPostMapper = mock(DiscussPostMapper.class);
        when(discussPostMapper.selectDiscussPosts(anyInt(), any(), any(), any(), anyInt(), anyInt(), anyInt()))
                .thenReturn(List.of());

        PostService service = new PostService(discussPostMapper);
        service.listPosts(Integer.MAX_VALUE, 50, PostService.ORDER_LATEST, null, null);

        ArgumentCaptor<Integer> offsetCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(discussPostMapper).selectDiscussPosts(eq(0), any(), any(), any(), offsetCaptor.capture(), anyInt(), anyInt());
        assertThat(offsetCaptor.getValue()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void commentServiceShouldNotPassNegativeOffsetWhenPageIsHuge() {
        CommentMapper commentMapper = mock(CommentMapper.class);
        when(commentMapper.selectCommentsByEntity(anyInt(), anyInt(), anyInt(), anyInt())).thenReturn(List.of());

        CommentService service = new CommentService(
                commentMapper,
                mock(PostService.class),
                mock(SensitiveFilter.class),
                mock(PostScoreQueue.class),
                mock(ContentEventPublisher.class),
                mock(SocialBlockQueryApi.class),
                mock(UserModerationGuard.class),
                new ContentTextCodec(new ContentRenderProperties())
        );

        service.listByPost(1, Integer.MAX_VALUE, 50);

        ArgumentCaptor<Integer> offsetCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(commentMapper).selectCommentsByEntity(eq(CommentService.ENTITY_TYPE_POST), eq(1), offsetCaptor.capture(), eq(50));
        assertThat(offsetCaptor.getValue()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void followServiceShouldNotPassNegativeOffsetWhenPageIsHuge() {
        FollowRepository followRepository = mock(FollowRepository.class);
        when(followRepository.listFollowers(anyInt(), anyInt(), anyInt(), anyInt())).thenReturn(List.of());

        FollowService service = new FollowService(
                followRepository,
                mock(SocialEventPublisher.class),
                mock(BlockService.class)
        );

        service.listFollowers(USER, 2, Integer.MAX_VALUE, 50);

        ArgumentCaptor<Integer> offsetCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(followRepository).listFollowers(eq(USER), eq(2), offsetCaptor.capture(), eq(50));
        assertThat(offsetCaptor.getValue()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void noticeServiceShouldNotPassNegativeOffsetWhenPageIsHuge() {
        MessageMapper messageMapper = mock(MessageMapper.class);
        when(messageMapper.selectNotices(anyInt(), any(), anyInt(), anyInt())).thenReturn(List.of());

        NoticeService service = new NoticeService(messageMapper, new MessageItemAssembler());
        service.listNotices(1, "comment", Integer.MAX_VALUE, 50);

        ArgumentCaptor<Integer> offsetCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(messageMapper).selectNotices(eq(1), eq("comment"), offsetCaptor.capture(), eq(50));
        assertThat(offsetCaptor.getValue()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void bookmarkServiceShouldNotPassNegativeOffsetWhenPageIsHuge() {
        BookmarkMapper bookmarkMapper = mock(BookmarkMapper.class);
        when(bookmarkMapper.selectBookmarkedPosts(anyInt(), anyInt(), anyInt())).thenReturn(List.of());

        BookmarkService service = new BookmarkService(
                bookmarkMapper,
                mock(PostService.class),
                mock(CommentService.class),
                mock(TagService.class),
                new PostSummaryAssembler(mock(ContentTextCodec.class))
        );
        service.listBookmarkedPosts(1, Integer.MAX_VALUE, 50);

        ArgumentCaptor<Integer> offsetCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(bookmarkMapper).selectBookmarkedPosts(eq(1), offsetCaptor.capture(), eq(50));
        assertThat(offsetCaptor.getValue()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void reportServiceShouldNotPassNegativeOffsetWhenPageIsHuge() {
        ReportMapper reportMapper = mock(ReportMapper.class);
        when(reportMapper.selectReports(any(), any(), any(), anyInt(), anyInt())).thenReturn(List.of());

        ReportService service = new ReportService(reportMapper, mock(PostService.class), mock(CommentMapper.class));
        service.listReports(null, null, null, Integer.MAX_VALUE, 100);

        ArgumentCaptor<Integer> offsetCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(reportMapper).selectReports(any(), any(), any(), offsetCaptor.capture(), eq(100));
        assertThat(offsetCaptor.getValue()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void moderationServiceShouldNotPassNegativeOffsetWhenPageIsHuge() {
        ModerationActionMapper actionMapper = mock(ModerationActionMapper.class);
        when(actionMapper.selectActions(any(), anyInt(), anyInt())).thenReturn(List.of());

        ModerationService service = new ModerationService(
                mock(ReportService.class),
                actionMapper
        );

        service.listActions(null, Integer.MAX_VALUE, 100);

        ArgumentCaptor<Integer> offsetCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(actionMapper).selectActions(any(), offsetCaptor.capture(), eq(100));
        assertThat(offsetCaptor.getValue()).isGreaterThanOrEqualTo(0);
    }
}
