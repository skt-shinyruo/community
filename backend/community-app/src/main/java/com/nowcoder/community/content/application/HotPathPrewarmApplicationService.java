package com.nowcoder.community.content.application;

import com.nowcoder.community.content.application.result.HotPathPrewarmResult;
import com.nowcoder.community.content.application.result.PostDetailResult;
import com.nowcoder.community.content.application.result.PostSummaryResult;
import com.nowcoder.community.content.domain.model.Category;
import com.nowcoder.community.content.domain.model.DiscussPost;
import com.nowcoder.community.content.domain.repository.CategoryContentRepository;
import com.nowcoder.community.content.domain.repository.PostContentRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class HotPathPrewarmApplicationService {

    private final PostContentRepository postRepository;
    private final CategoryContentRepository categoryRepository;
    private final PostFeedCache feedCache;
    private final PostSummaryCache summaryCache;
    private final PostFeedSummaryLoader summaryLoader;
    private final PostReadApplicationService postReadApplicationService;
    private final ContentFeedPolicyProperties feedProperties;
    private final ContentHotPathProperties hotPathProperties;
    private final HotPathSingleFlight singleFlight;

    public HotPathPrewarmApplicationService(
            PostContentRepository postRepository,
            CategoryContentRepository categoryRepository,
            PostFeedCache feedCache,
            PostSummaryCache summaryCache,
            PostFeedSummaryLoader summaryLoader,
            PostReadApplicationService postReadApplicationService,
            ContentFeedPolicyProperties feedProperties,
            ContentHotPathProperties hotPathProperties,
            HotPathSingleFlight singleFlight
    ) {
        this.postRepository = postRepository;
        this.categoryRepository = categoryRepository;
        this.feedCache = feedCache;
        this.summaryCache = summaryCache;
        this.summaryLoader = summaryLoader;
        this.postReadApplicationService = postReadApplicationService;
        this.feedProperties = feedProperties == null ? new ContentFeedPolicyProperties() : feedProperties;
        this.hotPathProperties = hotPathProperties == null ? new ContentHotPathProperties() : hotPathProperties;
        this.singleFlight = singleFlight == null ? loaderSingleFlight() : singleFlight;
    }

    public HotPathPrewarmResult prewarm() {
        return singleFlight.execute(
                "prewarm",
                "content-hot-path",
                hotPathProperties.getPrewarm().lockTtl(),
                this::prewarmUnlocked,
                () -> new HotPathPrewarmResult(0, 0, 0)
        );
    }

    private HotPathPrewarmResult prewarmUnlocked() {
        int pages = Math.max(1, hotPathProperties.getPrewarm().getPages());
        int pageSize = Math.max(1, Math.min(50, hotPathProperties.getPrewarm().getPageSize()));
        int boardLimit = Math.max(0, hotPathProperties.getPrewarm().getBoardLimit());
        String rankVersion = feedProperties.getHotRankVersion();
        Set<UUID> warmedDetailIds = new LinkedHashSet<>();
        int feedPages = 0;
        int summaries = 0;
        int details = 0;

        feedCache.writeRankVersion(rankVersion);
        for (int page = 0; page < pages; page++) {
            List<DiscussPost> posts = safePosts(postRepository.listPosts(page, pageSize, PostContentRepository.ORDER_HOT));
            WarmCounts counts = warmPosts(posts, null, rankVersion, warmedDetailIds);
            feedPages += posts.isEmpty() ? 0 : 1;
            summaries += counts.summaries();
            details += counts.details();
        }

        int boards = 0;
        for (Category category : safeCategories(categoryRepository.listCategories())) {
            if (category == null || category.getId() == null) {
                continue;
            }
            if (boards++ >= boardLimit) {
                break;
            }
            UUID boardId = category.getId();
            for (int page = 0; page < pages; page++) {
                List<DiscussPost> posts = safePosts(postRepository.listPosts(page, pageSize, PostContentRepository.ORDER_HOT, boardId, null));
                WarmCounts counts = warmPosts(posts, boardId, rankVersion, warmedDetailIds);
                feedPages += posts.isEmpty() ? 0 : 1;
                summaries += counts.summaries();
                details += counts.details();
            }
        }
        return new HotPathPrewarmResult(feedPages, summaries, details);
    }

    private WarmCounts warmPosts(List<DiscussPost> posts, UUID boardId, String rankVersion, Set<UUID> warmedDetailIds) {
        List<DiscussPost> validPosts = safePosts(posts).stream()
                .filter(post -> post != null && post.getId() != null)
                .toList();
        if (validPosts.isEmpty()) {
            return new WarmCounts(0, 0);
        }
        for (DiscussPost post : validPosts) {
            if (boardId == null) {
                feedCache.upsertGlobalHot(post.getId(), post.getScore(), rankVersion);
            } else {
                feedCache.upsertBoardHot(boardId, post.getId(), post.getScore(), rankVersion);
            }
        }

        List<PostSummaryResult> summaryResults = safeSummaries(summaryLoader.assembleSummaries(validPosts));
        summaryCache.putAll(summaryResults);

        int details = 0;
        for (DiscussPost post : validPosts) {
            if (!warmedDetailIds.add(post.getId())) {
                continue;
            }
            PostDetailResult detail = postReadApplicationService.getPostDetail(null, post.getId());
            if (detail != null) {
                details++;
            }
        }
        return new WarmCounts(summaryResults.size(), details);
    }

    private static List<DiscussPost> safePosts(List<DiscussPost> posts) {
        return posts == null ? List.of() : posts;
    }

    private static List<PostSummaryResult> safeSummaries(List<PostSummaryResult> summaries) {
        return summaries == null ? List.of() : summaries;
    }

    private static List<Category> safeCategories(List<Category> categories) {
        return categories == null ? List.of() : new ArrayList<>(categories);
    }

    private static HotPathSingleFlight loaderSingleFlight() {
        return new HotPathSingleFlight() {
            @Override
            public <T> T execute(String scope, String key, java.time.Duration ttl, java.util.function.Supplier<T> loader, java.util.function.Supplier<T> fallbackWhenBusy) {
                return loader.get();
            }
        };
    }

    private record WarmCounts(int summaries, int details) {
    }
}
