package com.nowcoder.community.content.infrastructure.persistence;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.content.domain.model.DiscussPost;
import com.nowcoder.community.content.infrastructure.persistence.mapper.BookmarkMapper;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BookmarkServiceTest {

    @Test
    void listBookmarkedPostsShouldReadPagedRows() {
        BookmarkMapper bookmarkMapper = mock(BookmarkMapper.class);
        UUID userId = uuid(7);
        UUID postId = uuid(11);

        DiscussPost post = new DiscussPost();
        post.setId(postId);

        when(bookmarkMapper.selectBookmarkedPosts(userId, 0, 10)).thenReturn(List.of(post));

        MyBatisBookmarkRepository service = new MyBatisBookmarkRepository(bookmarkMapper);

        List<DiscussPost> posts = service.listBookmarkedPosts(userId, 0, 10);

        assertThat(posts).containsExactly(post);
        verify(bookmarkMapper).selectBookmarkedPosts(userId, 0, 10);
    }

    @Test
    void addShouldUseAtomicActivePostInsertAndReportCreatedRow() {
        BookmarkMapper bookmarkMapper = mock(BookmarkMapper.class);
        UUID userId = uuid(21);
        UUID postId = uuid(22);
        when(bookmarkMapper.lockActivePost(postId)).thenReturn(postId);
        when(bookmarkMapper.insertBookmarkForActivePost(eq(userId), eq(postId), any(Date.class)))
                .thenReturn(1);

        boolean created = new MyBatisBookmarkRepository(bookmarkMapper).add(userId, postId);

        assertThat(created).isTrue();
        verify(bookmarkMapper, never()).existsActivePost(postId);
    }

    @Test
    void addShouldTreatZeroAffectedRowsAsIdempotentOnlyWhilePostRemainsActive() {
        BookmarkMapper bookmarkMapper = mock(BookmarkMapper.class);
        UUID userId = uuid(23);
        UUID postId = uuid(24);
        when(bookmarkMapper.lockActivePost(postId)).thenReturn(postId);
        when(bookmarkMapper.insertBookmarkForActivePost(eq(userId), eq(postId), any(Date.class)))
                .thenReturn(0);
        when(bookmarkMapper.existsActivePost(postId)).thenReturn(1);

        boolean created = new MyBatisBookmarkRepository(bookmarkMapper).add(userId, postId);

        assertThat(created).isFalse();
    }

    @Test
    void addShouldRejectZeroAffectedRowsWhenPostWasDeletedBeforeInsert() {
        BookmarkMapper bookmarkMapper = mock(BookmarkMapper.class);
        UUID userId = uuid(25);
        UUID postId = uuid(26);
        when(bookmarkMapper.lockActivePost(postId)).thenReturn(null);

        assertThatThrownBy(() -> new MyBatisBookmarkRepository(bookmarkMapper).add(userId, postId))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(com.nowcoder.community.content.exception.ContentErrorCode.POST_NOT_FOUND));
        verify(bookmarkMapper, never()).insertBookmarkForActivePost(eq(userId), eq(postId), any(Date.class));
    }

    @Test
    void removeShouldTreatMissingRelationAsIdempotentWhilePostExists() {
        BookmarkMapper bookmarkMapper = mock(BookmarkMapper.class);
        UUID userId = uuid(27);
        UUID postId = uuid(28);
        when(bookmarkMapper.lockPost(postId)).thenReturn(postId);
        when(bookmarkMapper.deleteBookmark(userId, postId)).thenReturn(0);

        boolean removed = new MyBatisBookmarkRepository(bookmarkMapper).remove(userId, postId);

        assertThat(removed).isFalse();
        verify(bookmarkMapper).deleteBookmark(userId, postId);
    }

    @Test
    void removeShouldRejectPostIdThatDoesNotExist() {
        BookmarkMapper bookmarkMapper = mock(BookmarkMapper.class);
        UUID userId = uuid(29);
        UUID postId = uuid(30);
        when(bookmarkMapper.lockPost(postId)).thenReturn(null);

        assertThatThrownBy(() -> new MyBatisBookmarkRepository(bookmarkMapper).remove(userId, postId))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(com.nowcoder.community.content.exception.ContentErrorCode.POST_NOT_FOUND));
        verify(bookmarkMapper, never()).deleteBookmark(userId, postId);
    }
}
