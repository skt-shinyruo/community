// MyBatis 收藏仓储：支持收藏/取消收藏、收藏状态查询与收藏列表分页。
package com.nowcoder.community.content.infrastructure.persistence;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.content.domain.repository.BookmarkRepository;
import com.nowcoder.community.content.infrastructure.persistence.mapper.BookmarkMapper;
import com.nowcoder.community.content.domain.model.DiscussPost;
import com.nowcoder.community.common.pagination.Pagination;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;
import java.util.UUID;

import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;
import static com.nowcoder.community.content.exception.ContentErrorCode.POST_NOT_FOUND;

@Repository
public class MyBatisBookmarkRepository implements BookmarkRepository {

    private final BookmarkMapper bookmarkMapper;

    public MyBatisBookmarkRepository(BookmarkMapper bookmarkMapper) {
        this.bookmarkMapper = bookmarkMapper;
    }

    @Override
    public boolean add(UUID userId, UUID postId) {
        if (userId == null || postId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "userId/postId 非法");
        }
        if (bookmarkMapper.lockActivePost(postId) == null) {
            throw new BusinessException(POST_NOT_FOUND);
        }
        if (bookmarkMapper.insertBookmarkForActivePost(userId, postId, new Date()) > 0) {
            return true;
        }
        if (bookmarkMapper.existsActivePost(postId) == 0) {
            throw new BusinessException(POST_NOT_FOUND);
        }
        return false;
    }

    @Override
    public boolean remove(UUID userId, UUID postId) {
        if (userId == null || postId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "userId/postId 非法");
        }
        if (bookmarkMapper.lockPost(postId) == null) {
            throw new BusinessException(POST_NOT_FOUND);
        }
        return bookmarkMapper.deleteBookmark(userId, postId) > 0;
    }

    @Override
    public boolean hasBookmarked(UUID userId, UUID postId) {
        if (userId == null || postId == null) {
            return false;
        }
        return bookmarkMapper.existsBookmark(userId, postId) > 0;
    }

    @Override
    public long countByPostId(UUID postId) {
        if (postId == null) {
            return 0L;
        }
        return Math.max(0L, bookmarkMapper.countByPostId(postId));
    }

    @Override
    public List<DiscussPost> listBookmarkedPosts(UUID userId, int page, int size) {
        if (userId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "userId 非法");
        }
        int p = Math.max(0, page);
        int s = Math.min(50, Math.max(1, size));
        return bookmarkMapper.selectBookmarkedPosts(userId, Pagination.safeOffset(p, s), s);
    }
}
