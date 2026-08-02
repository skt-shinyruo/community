package com.nowcoder.community.content.application;

import com.nowcoder.community.content.domain.model.Comment;
import com.nowcoder.community.content.domain.model.DiscussPost;
import com.nowcoder.community.content.domain.model.PostContentBlock;
import com.nowcoder.community.content.domain.model.PostMediaAsset;
import com.nowcoder.community.content.domain.repository.CommentContentRepository;
import com.nowcoder.community.content.domain.repository.PostContentBlockRepository;
import com.nowcoder.community.content.domain.repository.PostContentRepository;
import com.nowcoder.community.content.domain.repository.PostMediaAssetRepository;
import com.nowcoder.community.content.domain.repository.TagContentRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class PostReadTransactionOperations {

    private final PostContentRepository postContentRepository;
    private final CommentContentRepository commentContentRepository;
    private final TagContentRepository tagContentRepository;
    private final PostContentBlockRepository postContentBlockRepository;
    private final PostMediaAssetRepository postMediaAssetRepository;

    public PostReadTransactionOperations(
            PostContentRepository postContentRepository,
            CommentContentRepository commentContentRepository,
            TagContentRepository tagContentRepository,
            PostContentBlockRepository postContentBlockRepository,
            PostMediaAssetRepository postMediaAssetRepository
    ) {
        this.postContentRepository = postContentRepository;
        this.commentContentRepository = commentContentRepository;
        this.tagContentRepository = tagContentRepository;
        this.postContentBlockRepository = postContentBlockRepository;
        this.postMediaAssetRepository = postMediaAssetRepository;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public SummarySnapshot listPosts(
            int page,
            int size,
            int orderMode,
            UUID categoryId,
            String tag
    ) {
        return loadSummarySnapshot(postContentRepository.listPosts(page, size, orderMode, categoryId, tag));
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public SummarySnapshot listSubscribedPosts(
            UUID userId,
            List<UUID> subscribedCategoryIds,
            int page,
            int size,
            int orderMode,
            UUID categoryId,
            String tag
    ) {
        return loadSummarySnapshot(postContentRepository.listSubscribedPosts(
                userId,
                subscribedCategoryIds,
                page,
                size,
                orderMode,
                categoryId,
                tag
        ));
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public SummarySnapshot listPostsByUser(UUID userId, int page, int size) {
        return loadSummarySnapshot(postContentRepository.listPostsByUser(userId, page, size));
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public SummarySnapshot listPostsByIds(List<UUID> postIds) {
        return loadSummarySnapshot(postContentRepository.listPostsByIds(postIds));
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public DetailSnapshot getDetail(UUID postId) {
        DiscussPost post = postContentRepository.getById(postId);
        List<PostContentBlock> blocks = safeList(postContentBlockRepository.listByPostId(postId));
        List<String> tags = tagsFor(postId);
        List<UUID> assetIds = blocks.stream()
                .map(PostContentBlock::mediaAssetId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        List<PostMediaAsset> mediaAssets = assetIds.isEmpty()
                ? List.of()
                : safeList(postMediaAssetRepository.listByIds(assetIds));
        return new DetailSnapshot(post, blocks, tags, mediaAssets);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ProjectionSnapshot getProjectionAllowDeleted(UUID postId) {
        DiscussPost post = postContentRepository.getByIdAllowDeleted(postId);
        return new ProjectionSnapshot(post, tagsFor(postId), safeList(postContentBlockRepository.listByPostId(postId)));
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ProjectionBatchSnapshot scanPosts(UUID afterId, int limit) {
        List<DiscussPost> posts = safeList(postContentRepository.scanAfterId(afterId, limit));
        if (posts.isEmpty()) {
            return ProjectionBatchSnapshot.empty();
        }
        List<UUID> postIds = posts.stream().map(DiscussPost::getId).toList();
        return new ProjectionBatchSnapshot(
                posts,
                safeMap(tagContentRepository.getTagsByPostIds(postIds)),
                safeMap(postContentBlockRepository.listByPostIds(postIds))
        );
    }

    private SummarySnapshot loadSummarySnapshot(List<DiscussPost> rawPosts) {
        List<DiscussPost> posts = safeList(rawPosts);
        if (posts.isEmpty()) {
            return SummarySnapshot.empty();
        }
        List<UUID> postIds = posts.stream().map(DiscussPost::getId).toList();
        return new SummarySnapshot(
                posts,
                safeMap(commentContentRepository.getLatestPostActivitiesByPostIds(postIds)),
                safeMap(tagContentRepository.getTagsByPostIds(postIds)),
                safeMap(postContentBlockRepository.listByPostIds(postIds))
        );
    }

    private List<String> tagsFor(UUID postId) {
        return safeMap(tagContentRepository.getTagsByPostIds(List.of(postId)))
                .getOrDefault(postId, List.of());
    }

    private static <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    private static <K, V> Map<K, V> safeMap(Map<K, V> values) {
        return values == null ? Map.of() : Map.copyOf(values);
    }

    public record SummarySnapshot(
            List<DiscussPost> posts,
            Map<UUID, Comment> lastActivities,
            Map<UUID, List<String>> tagsByPostId,
            Map<UUID, List<PostContentBlock>> blocksByPostId
    ) {
        static SummarySnapshot empty() {
            return new SummarySnapshot(List.of(), Map.of(), Map.of(), Map.of());
        }
    }

    public record DetailSnapshot(
            DiscussPost post,
            List<PostContentBlock> blocks,
            List<String> tags,
            List<PostMediaAsset> mediaAssets
    ) {
    }

    public record ProjectionSnapshot(
            DiscussPost post,
            List<String> tags,
            List<PostContentBlock> blocks
    ) {
    }

    public record ProjectionBatchSnapshot(
            List<DiscussPost> posts,
            Map<UUID, List<String>> tagsByPostId,
            Map<UUID, List<PostContentBlock>> blocksByPostId
    ) {
        static ProjectionBatchSnapshot empty() {
            return new ProjectionBatchSnapshot(List.of(), Map.of(), Map.of());
        }
    }
}
