package com.nowcoder.community.content.infrastructure.persistence;

import com.nowcoder.community.content.domain.model.DiscussPost;
import com.nowcoder.community.content.domain.repository.PostContentRepository;
import com.nowcoder.community.content.infrastructure.persistence.mapper.DiscussPostMapper;
import com.nowcoder.community.common.id.UuidV7Generator;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MyBatisPostContentRepositoryTest {

    @Test
    void listPostsWithLookaheadShouldKeepLogicalPageOffsetAndFetchOneExtraRow() {
        DiscussPostMapper discussPostMapper = mock(DiscussPostMapper.class);
        MyBatisPostContentRepository repository = new MyBatisPostContentRepository(
                discussPostMapper, new UuidV7Generator());

        when(discussPostMapper.selectDiscussPosts(null, null, null, null, 100, 51, PostContentRepository.ORDER_HOT))
                .thenReturn(List.of());

        repository.listPosts(2, 50, 999, PostContentRepository.ORDER_HOT, null, null);

        verify(discussPostMapper)
                .selectDiscussPosts(null, null, null, null, 100, 51, PostContentRepository.ORDER_HOT);
    }

    @Test
    void listHotPostsAfterShouldForwardStableSortBoundaryAndClampLookaheadLimit() {
        DiscussPostMapper discussPostMapper = mock(DiscussPostMapper.class);
        MyBatisPostContentRepository repository = new MyBatisPostContentRepository(
                discussPostMapper, new UuidV7Generator());
        UUID boundaryPostId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        Date boundaryCreateTime = new Date(4_000L);

        when(discussPostMapper.selectHotPostsAfter(
                1,
                42.5,
                boundaryCreateTime,
                boundaryPostId,
                51,
                categoryId
        )).thenReturn(List.of());

        repository.listHotPostsAfter(1, 42.5, boundaryCreateTime, boundaryPostId, 999, categoryId);

        verify(discussPostMapper).selectHotPostsAfter(
                1,
                42.5,
                boundaryCreateTime,
                boundaryPostId,
                51,
                categoryId
        );
    }

    @Test
    void updateScoreShouldReturnThePersistedScoreVersion() {
        DiscussPostMapper discussPostMapper = mock(DiscussPostMapper.class);
        MyBatisPostContentRepository repository = new MyBatisPostContentRepository(
                discussPostMapper, new UuidV7Generator());
        UUID postId = UUID.randomUUID();
        DiscussPost updated = new DiscussPost();
        updated.setId(postId);
        updated.setStatus(DiscussPost.STATUS_NORMAL);
        updated.setAggregateVersion(7L);
        updated.setScoreVersion(4L);
        when(discussPostMapper.updateScoreIfVersion(postId, 12.5, 7L)).thenReturn(1);
        when(discussPostMapper.selectDiscussPostById(postId)).thenReturn(updated);

        assertThat(repository.updateScore(postId, 12.5, 7L)).isEqualTo(4L);

        verify(discussPostMapper).updateScoreIfVersion(postId, 12.5, 7L);
        verify(discussPostMapper).selectDiscussPostById(postId);
    }

    @Test
    void listRecentVisiblePostsByAuthorIdsShouldForwardCallerLimitAboveFiveHundred() {
        DiscussPostMapper discussPostMapper = mock(DiscussPostMapper.class);
        MyBatisPostContentRepository repository = new MyBatisPostContentRepository(
                discussPostMapper, new UuidV7Generator());
        UUID authorA = UUID.randomUUID();
        UUID authorB = UUID.randomUUID();

        when(discussPostMapper.selectRecentVisiblePostsByAuthorIds(List.of(authorA, authorB), 641))
                .thenReturn(List.of());

        repository.listRecentVisiblePostsByAuthorIds(List.of(authorA, authorB), 641);

        verify(discussPostMapper).selectRecentVisiblePostsByAuthorIds(List.of(authorA, authorB), 641);
    }

    @Test
    void listRecentVisiblePostsByAuthorIdsBeforeShouldForwardAnchorAndCallerLimit() {
        DiscussPostMapper discussPostMapper = mock(DiscussPostMapper.class);
        MyBatisPostContentRepository repository = new MyBatisPostContentRepository(
                discussPostMapper, new UuidV7Generator());
        UUID authorA = UUID.randomUUID();
        UUID authorB = UUID.randomUUID();
        UUID anchorPostId = UUID.randomUUID();
        Date anchorCreateTime = new Date(2_000L);

        when(discussPostMapper.selectRecentVisiblePostsByAuthorIdsBefore(
                List.of(authorA, authorB),
                anchorCreateTime,
                anchorPostId,
                21
        )).thenReturn(List.of());

        repository.listRecentVisiblePostsByAuthorIdsBefore(List.of(authorA, authorB), anchorCreateTime, anchorPostId, 21);

        verify(discussPostMapper).selectRecentVisiblePostsByAuthorIdsBefore(
                List.of(authorA, authorB),
                anchorCreateTime,
                anchorPostId,
                21
        );
    }
}
