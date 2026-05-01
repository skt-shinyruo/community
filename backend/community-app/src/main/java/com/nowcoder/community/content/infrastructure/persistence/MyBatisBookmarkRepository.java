// MyBatis 收藏仓储：支持收藏/取消收藏、收藏状态查询与收藏列表分页。
package com.nowcoder.community.content.infrastructure.persistence;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.content.domain.repository.BookmarkRepository;
import com.nowcoder.community.content.domain.repository.PostContentRepository;
import com.nowcoder.community.content.infrastructure.persistence.mapper.BookmarkMapper;
import com.nowcoder.community.content.domain.model.DiscussPost;
import com.nowcoder.community.infra.pagination.Pagination;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;
import java.util.UUID;

import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;

@Repository
public class MyBatisBookmarkRepository implements BookmarkRepository {

    private final BookmarkMapper bookmarkMapper;
    private final PostContentRepository postContentRepository;

    public MyBatisBookmarkRepository(
            BookmarkMapper bookmarkMapper,
            PostContentRepository postContentRepository
    ) {
        this.bookmarkMapper = bookmarkMapper;
        this.postContentRepository = postContentRepository;
    }

    @Override
    public void add(UUID userId, UUID postId) {
        if (userId == null || postId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "userId/postId 非法");
        }
        postContentRepository.getById(postId);
        bookmarkMapper.insertBookmark(userId, postId, new Date());
    }

    @Override
    public void remove(UUID userId, UUID postId) {
        if (userId == null || postId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "userId/postId 非法");
        }
        bookmarkMapper.deleteBookmark(userId, postId);
    }

    @Override
    public boolean hasBookmarked(UUID userId, UUID postId) {
        if (userId == null || postId == null) {
            return false;
        }
        return bookmarkMapper.existsBookmark(userId, postId) > 0;
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
