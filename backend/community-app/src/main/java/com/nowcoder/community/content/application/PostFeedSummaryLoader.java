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

import static com.nowcoder.community.content.application.PostReadTransactionOperations.SummarySnapshot;

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
            List<DiscussPost> loadedPosts = postContentRepository.listPostsByIds(missingIds);
            List<PostSummaryResult> loaded = assemble(loadedPosts);
            cache(loadedPosts, loaded);
            loaded.forEach(item -> cached.put(item.id(), item));
        }
        return postIds.stream()
                .map(cached::get)
                .filter(Objects::nonNull)
                .toList();
    }

    public List<PostSummaryResult> serveCurrentPosts(List<DiscussPost> posts) {
        List<PostSummaryResult> summaries = assemble(posts);
        try {
            cache(posts, summaries);
        } catch (RuntimeException ignored) {
            // Cache backfill is best-effort on the request-serving fallback path.
        }
        return summaries;
    }

    public List<PostSummaryResult> prewarmCurrentPosts(List<DiscussPost> posts) {
        List<PostSummaryResult> summaries = assemble(posts);
        cache(posts, summaries);
        return summaries;
    }

    public List<PostSummaryResult> readSnapshot(SummarySnapshot snapshot) {
        if (snapshot == null) {
            return List.of();
        }
        return assemble(
                snapshot.posts(),
                snapshot.lastActivities(),
                snapshot.tagsByPostId(),
                snapshot.blocksByPostId()
        );
    }

    private List<PostSummaryResult> assemble(List<DiscussPost> posts) {
        if (posts == null || posts.isEmpty()) {
            return List.of();
        }
        List<UUID> postIds = posts.stream().map(DiscussPost::getId).toList();
        Map<UUID, Comment> lastActivities = commentContentRepository.getLatestPostActivitiesByPostIds(postIds);
        Map<UUID, List<String>> tagsByPostId = tagContentRepository.getTagsByPostIds(postIds);
        Map<UUID, List<PostContentBlock>> blocksByPostId = postContentBlockRepository.listByPostIds(postIds);
        return assemble(posts, lastActivities, tagsByPostId, blocksByPostId);
    }

    private List<PostSummaryResult> assemble(
            List<DiscussPost> posts,
            Map<UUID, Comment> lastActivities,
            Map<UUID, List<String>> tagsByPostId,
            Map<UUID, List<PostContentBlock>> blocksByPostId
    ) {
        if (posts == null || posts.isEmpty()) {
            return List.of();
        }
        Map<UUID, Comment> safeActivities = lastActivities == null ? Map.of() : lastActivities;
        Map<UUID, List<String>> safeTags = tagsByPostId == null ? Map.of() : tagsByPostId;
        Map<UUID, List<PostContentBlock>> safeBlocks = blocksByPostId == null ? Map.of() : blocksByPostId;
        return posts.stream()
                .map(post -> postSummaryAssembler.assemble(
                        post,
                        safeActivities.get(post.getId()),
                        safeTags.get(post.getId()),
                        postContentBlockTextProjector.preview(safeBlocks.get(post.getId()), 240)
                ))
                .toList();
    }

    private void cache(List<DiscussPost> posts, List<PostSummaryResult> summaries) {
        if (posts == null || posts.isEmpty() || summaries == null || summaries.isEmpty()) {
            return;
        }
        Map<UUID, SummaryVersion> versionsByPostId = posts.stream()
                .filter(post -> post != null && post.getId() != null)
                .collect(java.util.stream.Collectors.toMap(
                        DiscussPost::getId,
                        post -> new SummaryVersion(post.getAggregateVersion(), post.getScoreVersion()),
                        SummaryVersion::max
                ));
        postSummaryCache.putVersioned(summaries.stream()
                .filter(summary -> summary != null
                        && summary.id() != null
                        && versionsByPostId.containsKey(summary.id()))
                .map(summary -> {
                    SummaryVersion version = versionsByPostId.get(summary.id());
                    return new PostSummaryCache.VersionedSummary(
                            summary,
                            version.aggregateVersion(),
                            version.scoreVersion()
                    );
                })
                .toList());
    }

    private record SummaryVersion(long aggregateVersion, long scoreVersion) {

        private SummaryVersion max(SummaryVersion other) {
            if (other == null
                    || aggregateVersion > other.aggregateVersion
                    || (aggregateVersion == other.aggregateVersion && scoreVersion >= other.scoreVersion)) {
                return this;
            }
            return other;
        }
    }
}
