// 收藏服务：支持收藏/取消收藏、收藏状态查询与收藏列表分页。
package com.nowcoder.community.content.service;

import com.nowcoder.community.contracts.exception.BusinessException;
import com.nowcoder.community.content.dao.BookmarkMapper;
import com.nowcoder.community.content.entity.DiscussPost;
import com.nowcoder.community.infra.pagination.Pagination;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

import static com.nowcoder.community.contracts.api.CommonErrorCode.INVALID_ARGUMENT;

@Service
public class BookmarkService {

    private final BookmarkMapper bookmarkMapper;
    private final PostService postService;

    public BookmarkService(BookmarkMapper bookmarkMapper, PostService postService) {
        this.bookmarkMapper = bookmarkMapper;
        this.postService = postService;
    }

    public void add(int userId, int postId) {
        if (userId <= 0 || postId <= 0) {
            throw new BusinessException(INVALID_ARGUMENT, "userId/postId 非法");
        }
        // 校验帖子存在且未删除
        postService.getById(postId);
        bookmarkMapper.insertBookmark(userId, postId, new Date());
    }

    public void remove(int userId, int postId) {
        if (userId <= 0 || postId <= 0) {
            throw new BusinessException(INVALID_ARGUMENT, "userId/postId 非法");
        }
        bookmarkMapper.deleteBookmark(userId, postId);
    }

    public boolean hasBookmarked(int userId, int postId) {
        if (userId <= 0 || postId <= 0) {
            return false;
        }
        return bookmarkMapper.existsBookmark(userId, postId) > 0;
    }

    public List<DiscussPost> listBookmarkedPosts(int userId, int page, int size) {
        if (userId <= 0) {
            throw new BusinessException(INVALID_ARGUMENT, "userId 非法");
        }
        int p = Math.max(0, page);
        int s = Math.min(50, Math.max(1, size));
        return bookmarkMapper.selectBookmarkedPosts(userId, Pagination.safeOffset(p, s), s);
    }
}
