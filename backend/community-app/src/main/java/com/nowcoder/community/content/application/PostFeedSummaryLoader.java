package com.nowcoder.community.content.application;

import com.nowcoder.community.content.application.result.PostSummaryResult;
import com.nowcoder.community.content.domain.model.Comment;
import com.nowcoder.community.content.domain.model.DiscussPost;
import com.nowcoder.community.content.domain.model.PostContentBlock;
import com.nowcoder.community.content.domain.repository.CommentContentRepository;
import com.nowcoder.community.content.domain.repository.PostContentBlockRepository;
import com.nowcoder.community.content.domain.repository.PostContentRepository;
import com.nowcoder.community.content.domain.repository.TagContentRepository;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Component
public class PostFeedSummaryLoader {

    private final PostContentRepository postContentRepository;
    private final CommentContentRepository commentContentRepository;
    private final TagContentRepository tagContentRepository;
    private final PostContentBlockRepository postContentBlockRepository;
    private final PostSummaryCache postSummaryCache;
    private final PostContentBlockTextProjector postContentBlockTextProjector;
    private final PostSummaryAssembler postSummaryAssembler;

    public PostFeedSummaryLoader(
            PostContentRepository postContentRepository,
            CommentContentRepository commentContentRepository,
            TagContentRepository tagContentRepository,
            PostContentBlockRepository postContentBlockRepository,
            PostSummaryCache postSummaryCache,
            PostContentBlockTextProjector postContentBlockTextProjector,
            PostSummaryAssembler postSummaryAssembler
    ) {
        this.postContentRepository = postContentRepository;
        this.commentContentRepository = commentContentRepository;
        this.tagContentRepository = tagContentRepository;
        this.postContentBlockRepository = postContentBlockRepository;
        this.postSummaryCache = postSummaryCache;
        this.postContentBlockTextProjector = postContentBlockTextProjector;
        this.postSummaryAssembler = postSummaryAssembler;
    }

    public List<PostSummaryResult> readSummaries(List<UUID> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return List.of();
        }
        Map<UUID, PostSummaryResult> cachedEntries = postSummaryCache.getAll(postIds);
        Map<UUID, PostSummaryResult> cached = new LinkedHashMap<>(cachedEntries == null ? Map.of() : cachedEntries);
        List<UUID> missingIds = postIds.stream()
                .filter(id -> !cached.containsKey(id))
                .toList();
        if (!missingIds.isEmpty()) {
            List<PostSummaryResult> loaded = assembleSummaries(postContentRepository.listPostsByIds(missingIds));
            postSummaryCache.putAll(loaded);
            loaded.forEach(item -> cached.put(item.id(), item));
        }
        return postIds.stream()
                .map(cached::get)
                .filter(Objects::nonNull)
                .toList();
    }

    public List<PostSummaryResult> assembleSummaries(List<DiscussPost> posts) {
        if (posts == null || posts.isEmpty()) {
            return List.of();
        }
        List<UUID> postIds = posts.stream().map(DiscussPost::getId).toList();
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
