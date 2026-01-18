package com.nowcoder.community.content.service;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.content.dao.DiscussPostMapper;
import com.nowcoder.community.content.entity.DiscussPost;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.nowcoder.community.common.api.CommonErrorCode.NOT_FOUND;

@Service
public class PostService {

    public static final int ORDER_LATEST = 0;
    public static final int ORDER_HOT = 1;

    private final DiscussPostMapper discussPostMapper;

    public PostService(DiscussPostMapper discussPostMapper) {
        this.discussPostMapper = discussPostMapper;
    }

    public List<DiscussPost> listPosts(int page, int size, int orderMode) {
        int p = Math.max(0, page);
        int s = Math.min(50, Math.max(1, size));
        return discussPostMapper.selectDiscussPosts(0, p * s, s, orderMode);
    }

    public DiscussPost getById(int postId) {
        DiscussPost post = discussPostMapper.selectDiscussPostById(postId);
        if (post == null) {
            throw new BusinessException(NOT_FOUND, "帖子不存在");
        }
        return post;
    }

    public int create(DiscussPost post) {
        discussPostMapper.insertDiscussPost(post);
        return post.getId();
    }

    public void updateCommentCount(int postId, int commentCount) {
        discussPostMapper.updateCommentCount(postId, commentCount);
    }

    public void updateType(int postId, int type) {
        discussPostMapper.updateType(postId, type);
    }

    public void updateStatus(int postId, int status) {
        discussPostMapper.updateStatus(postId, status);
    }

    public void updateScore(int postId, double score) {
        discussPostMapper.updateScore(postId, score);
    }
}
