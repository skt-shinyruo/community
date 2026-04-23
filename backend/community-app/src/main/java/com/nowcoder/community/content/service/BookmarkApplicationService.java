package com.nowcoder.community.content.service;

import com.nowcoder.community.content.api.model.PostSummaryView;
import com.nowcoder.community.content.dto.PostSummaryResponse;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class BookmarkApplicationService {

    private final BookmarkService bookmarkService;

    public BookmarkApplicationService(BookmarkService bookmarkService) {
        this.bookmarkService = bookmarkService;
    }

    public void add(UUID userId, UUID postId) {
        bookmarkService.add(userId, postId);
    }

    public void remove(UUID userId, UUID postId) {
        bookmarkService.remove(userId, postId);
    }

    public List<PostSummaryResponse> listBookmarkedPostSummaryResponses(UUID userId, int page, int size) {
        return bookmarkService.listBookmarkedPostSummaries(userId, page, size).stream()
                .map(this::toPostSummaryResponse)
                .toList();
    }

    private PostSummaryResponse toPostSummaryResponse(PostSummaryView view) {
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
}
