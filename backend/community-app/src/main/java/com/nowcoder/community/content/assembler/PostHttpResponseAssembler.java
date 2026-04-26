package com.nowcoder.community.content.assembler;

import com.nowcoder.community.content.api.model.CommentView;
import com.nowcoder.community.content.api.model.PostCreateResult;
import com.nowcoder.community.content.api.model.PostDetailView;
import com.nowcoder.community.content.api.model.PostSummaryView;
import com.nowcoder.community.content.dto.CommentResponse;
import com.nowcoder.community.content.dto.CreatePostResponse;
import com.nowcoder.community.content.dto.PostDetailResponse;
import com.nowcoder.community.content.dto.PostSummaryResponse;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PostHttpResponseAssembler {

    public List<PostSummaryResponse> toPostSummaryResponses(List<PostSummaryView> views) {
        if (views == null || views.isEmpty()) {
            return List.of();
        }
        return views.stream().map(this::toPostSummaryResponse).toList();
    }

    public PostSummaryResponse toPostSummaryResponse(PostSummaryView view) {
        PostSummaryResponse response = new PostSummaryResponse();
        response.setId(view.id());
        response.setUserId(view.userId());
        response.setTitle(view.title());
        response.setType(view.type());
        response.setStatus(view.status());
        response.setCreateTime(view.createTime());
        response.setCommentCount(view.commentCount());
        response.setScore(view.score());
        response.setCategoryId(view.categoryId());
        response.setTags(view.tags());
        response.setLastReplyUserId(view.lastReplyUserId());
        response.setLastReplyTime(view.lastReplyTime());
        response.setLastActivityTime(view.lastActivityTime());
        response.setLastReplyPreview(view.lastReplyPreview());
        return response;
    }

    public PostDetailResponse toPostDetailResponse(PostDetailView view) {
        PostDetailResponse response = new PostDetailResponse();
        response.setId(view.id());
        response.setUserId(view.userId());
        response.setTitle(view.title());
        response.setContent(view.content());
        response.setType(view.type());
        response.setStatus(view.status());
        response.setCreateTime(view.createTime());
        response.setUpdateTime(view.updateTime());
        response.setEditCount(view.editCount());
        response.setCommentCount(view.commentCount());
        response.setScore(view.score());
        response.setCategoryId(view.categoryId());
        response.setTags(view.tags());
        response.setLikeCount(view.likeCount());
        response.setLiked(view.liked());
        response.setBookmarked(view.bookmarked());
        return response;
    }

    public List<CommentResponse> toCommentResponses(List<CommentView> views) {
        if (views == null || views.isEmpty()) {
            return List.of();
        }
        return views.stream().map(this::toCommentResponse).toList();
    }

    public CommentResponse toCommentResponse(CommentView view) {
        return CommentResponse.from(view);
    }

    public CreatePostResponse toCreatePostResponse(PostCreateResult result) {
        CreatePostResponse response = new CreatePostResponse();
        response.setPostId(result == null ? null : result.postId());
        return response;
    }
}
