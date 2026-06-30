package com.nowcoder.community.infra.pagination;

import com.nowcoder.community.content.infrastructure.persistence.mapper.BookmarkMapper;
import com.nowcoder.community.content.infrastructure.persistence.mapper.CommentMapper;
import com.nowcoder.community.content.infrastructure.persistence.mapper.DiscussPostMapper;
import com.nowcoder.community.content.infrastructure.persistence.mapper.ModerationActionMapper;
import com.nowcoder.community.content.infrastructure.persistence.mapper.ReportMapper;
import com.nowcoder.community.content.infrastructure.persistence.MyBatisBookmarkRepository;
import com.nowcoder.community.content.infrastructure.persistence.MyBatisCommentContentRepository;
import com.nowcoder.community.content.infrastructure.persistence.MyBatisModerationQueryRepository;
import com.nowcoder.community.content.infrastructure.persistence.MyBatisPostContentRepository;
import com.nowcoder.community.content.infrastructure.persistence.MyBatisReportContentRepository;
import com.nowcoder.community.content.domain.repository.PostContentRepository;
import com.nowcoder.community.notice.application.NoticeApplicationService;
import com.nowcoder.community.notice.domain.repository.NoticeRepository;
import com.nowcoder.community.social.application.FollowApplicationService;
import com.nowcoder.community.social.domain.event.SocialDomainEventPublisher;
import com.nowcoder.community.social.domain.repository.BlockRepository;
import com.nowcoder.community.social.domain.repository.FollowRepository;
import com.nowcoder.community.social.domain.service.BlockDomainService;
import com.nowcoder.community.social.domain.service.FollowDomainService;
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

        MyBatisPostContentRepository service = new MyBatisPostContentRepository(discussPostMapper);
        service.listPosts(Integer.MAX_VALUE, 50, MyBatisPostContentRepository.ORDER_LATEST, null, null);

        ArgumentCaptor<Integer> offsetCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(discussPostMapper).selectDiscussPosts(eq((UUID) null), any(), any(), any(), offsetCaptor.capture(), anyInt(), anyInt());
        assertThat(offsetCaptor.getValue()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void commentServiceShouldNotPassNegativeOffsetWhenPageIsHuge() {
        CommentMapper commentMapper = mock(CommentMapper.class);
        when(commentMapper.selectCommentsByEntity(anyInt(), any(), anyInt(), anyInt())).thenReturn(List.of());
        UUID postId = uuid(1);

        MyBatisCommentContentRepository service = new MyBatisCommentContentRepository(commentMapper, mock(PostContentRepository.class));

        service.listByPost(postId, Integer.MAX_VALUE, 50);

        ArgumentCaptor<Integer> offsetCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(commentMapper).selectCommentsByEntity(eq(MyBatisCommentContentRepository.ENTITY_TYPE_POST), eq(postId), offsetCaptor.capture(), eq(50));
        assertThat(offsetCaptor.getValue()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void followServiceShouldNotPassNegativeOffsetWhenPageIsHuge() {
        FollowRepository followRepository = mock(FollowRepository.class);
        BlockRepository blockRepository = mock(BlockRepository.class);
        when(followRepository.listFollowersExcludingBlocked(anyInt(), any(), any(), anyInt(), anyInt())).thenReturn(List.of());
        UUID userId = uuid(2);

        FollowApplicationService service = new FollowApplicationService(
                followRepository,
                blockRepository,
                new FollowDomainService(),
                new BlockDomainService(),
                mock(SocialDomainEventPublisher.class)
        );

        service.listFollowers(USER, userId, Integer.MAX_VALUE, 50);

        ArgumentCaptor<Integer> offsetCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(followRepository).listFollowersExcludingBlocked(eq(USER), eq(userId), eq(blockRepository), offsetCaptor.capture(), eq(50));
        assertThat(offsetCaptor.getValue()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void noticeServiceShouldNotPassNegativeOffsetWhenPageIsHuge() {
        NoticeRepository noticeRepository = mock(NoticeRepository.class);
        when(noticeRepository.findByUserAndTopic(any(), any(), anyInt(), anyInt())).thenReturn(List.of());
        UUID userId = uuid(1);

        NoticeApplicationService service = new NoticeApplicationService(noticeRepository);
        service.listNotices(userId, "comment", Integer.MAX_VALUE, 50);

        ArgumentCaptor<Integer> offsetCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(noticeRepository).findByUserAndTopic(eq(userId), eq("comment"), offsetCaptor.capture(), eq(50));
        assertThat(offsetCaptor.getValue()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void bookmarkServiceShouldNotPassNegativeOffsetWhenPageIsHuge() {
        BookmarkMapper bookmarkMapper = mock(BookmarkMapper.class);
        when(bookmarkMapper.selectBookmarkedPosts(any(), anyInt(), anyInt())).thenReturn(List.of());
        UUID userId = uuid(1);

        MyBatisBookmarkRepository service = new MyBatisBookmarkRepository(
                bookmarkMapper,
                mock(MyBatisPostContentRepository.class)
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

        MyBatisReportContentRepository service = new MyBatisReportContentRepository(reportMapper);
        service.listReports(null, null, null, Integer.MAX_VALUE, 100);

        ArgumentCaptor<Integer> offsetCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(reportMapper).selectReports(any(), any(), any(), offsetCaptor.capture(), eq(100));
        assertThat(offsetCaptor.getValue()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void moderationServiceShouldNotPassNegativeOffsetWhenPageIsHuge() {
        ModerationActionMapper actionMapper = mock(ModerationActionMapper.class);
        when(actionMapper.selectActions(any(), anyInt(), anyInt())).thenReturn(List.of());

        MyBatisModerationQueryRepository service = new MyBatisModerationQueryRepository(
                mock(MyBatisReportContentRepository.class),
                actionMapper
        );

        service.listActions(null, Integer.MAX_VALUE, 100);

        ArgumentCaptor<Integer> offsetCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(actionMapper).selectActions(any(), offsetCaptor.capture(), eq(100));
        assertThat(offsetCaptor.getValue()).isGreaterThanOrEqualTo(0);
    }
}
