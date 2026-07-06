package com.nowcoder.community.content.application;

import com.nowcoder.community.common.constants.EntityTypes;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.content.application.result.ResolvedContentResult;
import com.nowcoder.community.content.domain.model.Comment;
import com.nowcoder.community.content.domain.model.DiscussPost;
import com.nowcoder.community.content.domain.repository.CommentContentRepository;
import com.nowcoder.community.content.domain.repository.PostContentRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;
import static com.nowcoder.community.content.exception.ContentErrorCode.COMMENT_NOT_FOUND;
import static com.nowcoder.community.content.exception.ContentErrorCode.POST_NOT_FOUND;

@Service
public class ContentEntityResolutionApplicationService {

    private final PostContentRepository postContentRepository;
    private final CommentContentRepository commentContentRepository;

    public ContentEntityResolutionApplicationService(
            PostContentRepository postContentRepository,
            CommentContentRepository commentContentRepository
    ) {
        this.postContentRepository = postContentRepository;
        this.commentContentRepository = commentContentRepository;
    }

    public ResolvedContentResult resolve(int entityType, UUID entityId) {
        if (entityId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "entityId 非法");
        }
        if (entityType == EntityTypes.POST) {
            return resolvePost(entityId);
        }
        if (entityType == EntityTypes.COMMENT) {
            return resolveComment(entityId);
        }
        throw new BusinessException(INVALID_ARGUMENT, "仅支持解析 POST/COMMENT");
    }

    private ResolvedContentResult resolvePost(UUID postId) {
        DiscussPost post = postContentRepository.getById(postId);
        return new ResolvedContentResult(post.getUserId(), post.getId());
    }

    private ResolvedContentResult resolveComment(UUID commentId) {
        Comment comment = commentContentRepository.getByIdAllowDeleted(commentId);
        if (!comment.isActive()) {
            throw new BusinessException(COMMENT_NOT_FOUND);
        }
        UUID postId = comment.getPostId();
        if (postId == null) {
            throw new BusinessException(POST_NOT_FOUND, "评论所属帖子不存在");
        }
        postContentRepository.getById(postId);
        return new ResolvedContentResult(comment.getUserId(), postId);
    }
}
