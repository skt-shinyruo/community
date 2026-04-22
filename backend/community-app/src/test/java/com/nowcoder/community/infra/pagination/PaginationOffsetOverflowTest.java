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
import com.nowcoder.community.notice.mapper.NoticeMapper;
import com.nowcoder.community.notice.service.NoticeService;
import com.nowcoder.community.social.api.query.SocialBlockQueryApi;
import com.nowcoder.community.social.block.BlockService;
import com.nowcoder.community.social.event.SocialEventPublisher;
import com.nowcoder.community.social.follow.FollowRepository;
import com.nowcoder.community.social.follow.FollowService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.UUID;

import static com.nowcoder.community.common.constants.EntityTypes.USER;
import static com.nowcoder.community.support.TestUuids.uuid;
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
        when(discussPostMapper.selectDiscussPosts(any(), any(), any(), any(), anyInt(), anyInt(), anyInt()))
                .thenReturn(List.of());

        PostService service = new PostService(discussPostMapper);
        service.listPosts(Integer.MAX_VALUE, 50, PostService.ORDER_LATEST, null, null);

        ArgumentCaptor<Integer> offsetCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(discussPostMapper).selectDiscussPosts(eq((UUID) null), any(), any(), any(), offsetCaptor.capture(), anyInt(), anyInt());
        assertThat(offsetCaptor.getValue()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void commentServiceShouldNotPassNegativeOffsetWhenPageIsHuge() {
        CommentMapper commentMapper = mock(CommentMapper.class);
        when(commentMapper.selectCommentsByEntity(anyInt(), any(), anyInt(), anyInt())).thenReturn(List.of());
        UUID postId = uuid(1);

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

        service.listByPost(postId, Integer.MAX_VALUE, 50);

        ArgumentCaptor<Integer> offsetCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(commentMapper).selectCommentsByEntity(eq(CommentService.ENTITY_TYPE_POST), eq(postId), offsetCaptor.capture(), eq(50));
        assertThat(offsetCaptor.getValue()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void followServiceShouldNotPassNegativeOffsetWhenPageIsHuge() {
        FollowRepository followRepository = mock(FollowRepository.class);
        when(followRepository.listFollowers(anyInt(), any(), anyInt(), anyInt())).thenReturn(List.of());
        UUID userId = uuid(2);

        FollowService service = new FollowService(
                followRepository,
                mock(SocialEventPublisher.class),
                mock(BlockService.class)
        );

        service.listFollowers(USER, userId, Integer.MAX_VALUE, 50);

        ArgumentCaptor<Integer> offsetCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(followRepository).listFollowers(eq(USER), eq(userId), offsetCaptor.capture(), eq(50));
        assertThat(offsetCaptor.getValue()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void noticeServiceShouldNotPassNegativeOffsetWhenPageIsHuge() {
        NoticeMapper noticeMapper = mock(NoticeMapper.class);
        when(noticeMapper.selectNotices(any(), any(), anyInt(), anyInt())).thenReturn(List.of());
        UUID userId = uuid(1);

        NoticeService service = new NoticeService(noticeMapper);
        service.listNotices(userId, "comment", Integer.MAX_VALUE, 50);

        ArgumentCaptor<Integer> offsetCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(noticeMapper).selectNotices(eq(userId), eq("comment"), offsetCaptor.capture(), eq(50));
        assertThat(offsetCaptor.getValue()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void bookmarkServiceShouldNotPassNegativeOffsetWhenPageIsHuge() {
        BookmarkMapper bookmarkMapper = mock(BookmarkMapper.class);
        when(bookmarkMapper.selectBookmarkedPosts(any(), anyInt(), anyInt())).thenReturn(List.of());
        UUID userId = uuid(1);

        BookmarkService service = new BookmarkService(
                bookmarkMapper,
                mock(PostService.class),
                mock(CommentService.class),
                mock(TagService.class),
                new PostSummaryAssembler(mock(ContentTextCodec.class))
        );
        service.listBookmarkedPosts(userId, Integer.MAX_VALUE, 50);

        ArgumentCaptor<Integer> offsetCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(bookmarkMapper).selectBookmarkedPosts(eq(userId), offsetCaptor.capture(), eq(50));
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
