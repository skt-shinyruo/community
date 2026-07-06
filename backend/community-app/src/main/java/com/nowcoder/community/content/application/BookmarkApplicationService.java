package com.nowcoder.community.content.application;

import com.nowcoder.community.content.application.result.PostSummaryResult;
import com.nowcoder.community.content.domain.model.Comment;
import com.nowcoder.community.content.domain.model.DiscussPost;
import com.nowcoder.community.content.domain.model.PostContentBlock;
import com.nowcoder.community.content.domain.repository.BookmarkRepository;
import com.nowcoder.community.content.domain.repository.CommentContentRepository;
import com.nowcoder.community.content.domain.repository.PostContentBlockRepository;
import com.nowcoder.community.content.domain.repository.TagContentRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class BookmarkApplicationService {

    private final BookmarkRepository bookmarkRepository;
    private final CommentContentRepository commentContentRepository;
    private final TagContentRepository tagContentRepository;
    private final PostContentBlockRepository postContentBlockRepository;
    private final PostContentBlockTextProjector postContentBlockTextProjector;
    private final PostCounterCache postCounterCache;
    private final PostSummaryAssembler postSummaryAssembler;

    public BookmarkApplicationService(
            BookmarkRepository bookmarkRepository,
            CommentContentRepository commentContentRepository,
            TagContentRepository tagContentRepository,
            PostContentBlockRepository postContentBlockRepository,
            PostContentBlockTextProjector postContentBlockTextProjector,
            PostCounterCache postCounterCache,
            PostSummaryAssembler postSummaryAssembler
    ) {
        this.bookmarkRepository = bookmarkRepository;
        this.commentContentRepository = commentContentRepository;
        this.tagContentRepository = tagContentRepository;
        this.postContentBlockRepository = postContentBlockRepository;
        this.postContentBlockTextProjector = postContentBlockTextProjector;
        this.postCounterCache = postCounterCache;
        this.postSummaryAssembler = postSummaryAssembler;
    }

    public void add(UUID userId, UUID postId) {
        if (bookmarkRepository.add(userId, postId)) {
            postCounterCache.incrementBookmarkCount(postId, 1L);
        }
    }

    public void remove(UUID userId, UUID postId) {
        if (bookmarkRepository.remove(userId, postId)) {
            postCounterCache.incrementBookmarkCount(postId, -1L);
        }
    }

    public List<PostSummaryResult> listBookmarkedPostSummaries(UUID userId, int page, int size) {
        List<DiscussPost> posts = bookmarkRepository.listBookmarkedPosts(userId, page, size);
        if (posts == null || posts.isEmpty()) {
            return List.of();
        }

        List<UUID> postIds = posts.stream()
                .map(DiscussPost::getId)
                .toList();
        Map<UUID, Comment> lastActivities = commentContentRepository.getLatestPostActivitiesByPostIds(postIds);
        Map<UUID, List<String>> tagsByPostId = tagContentRepository.getTagsByPostIds(postIds);
        Map<UUID, List<PostContentBlock>> blocksByPostId = postContentBlockRepository.listByPostIds(postIds);
        return posts.stream()
                .map(post -> postSummaryAssembler.assemble(
                        post,
                        lastActivities.get(post.getId()),
                        tagsByPostId.get(post.getId()),
                        postContentBlockTextProjector.preview(blocksByPostId.get(post.getId()), 240)
                ))
                .toList();
    }
}
