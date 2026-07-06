package com.nowcoder.community.content.application;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.content.application.result.FeedPageResult;
import com.nowcoder.community.content.application.result.PostSummaryResult;
import com.nowcoder.community.content.domain.model.DiscussPost;
import com.nowcoder.community.content.domain.repository.CommentContentRepository;
import com.nowcoder.community.content.domain.repository.PostContentBlockRepository;
import com.nowcoder.community.content.domain.repository.PostContentRepository;
import com.nowcoder.community.content.domain.repository.TagContentRepository;
import com.nowcoder.community.social.api.query.SocialFollowQueryApi;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.UUID;

import static com.nowcoder.community.common.exception.CommonErrorCode.UNAUTHORIZED;

@Service
public class FollowFeedReadApplicationService {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 50;
    private static final int FOLLOWEE_LIMIT = 200;
    private static final String RANK_VERSION = "follow-v1";

    private final SocialFollowQueryApi followQueryApi;
    private final PostContentRepository postContentRepository;
    private final FollowFeedCache followFeedCache;
    private final PostFeedSummaryLoader postFeedSummaryLoader;
    private final FollowFeedCursorCodec followFeedCursorCodec;

    public FollowFeedReadApplicationService(
            SocialFollowQueryApi followQueryApi,
            PostContentRepository postContentRepository,
            FollowFeedCache followFeedCache,
            PostFeedSummaryLoader postFeedSummaryLoader,
            FollowFeedCursorCodec followFeedCursorCodec
    ) {
        this.followQueryApi = followQueryApi;
        this.postContentRepository = postContentRepository;
        this.followFeedCache = followFeedCache;
        this.postFeedSummaryLoader = postFeedSummaryLoader;
        this.followFeedCursorCodec = followFeedCursorCodec;
    }

    public FollowFeedReadApplicationService(
            SocialFollowQueryApi followQueryApi,
            PostContentRepository postContentRepository,
            FollowFeedCache followFeedCache,
            CommentContentRepository commentContentRepository,
            TagContentRepository tagContentRepository,
            PostContentBlockRepository postContentBlockRepository,
            PostSummaryCache postSummaryCache,
            PostContentBlockTextProjector postContentBlockTextProjector,
            PostSummaryAssembler postSummaryAssembler,
            FollowFeedCursorCodec followFeedCursorCodec
    ) {
        this(
                followQueryApi,
                postContentRepository,
                followFeedCache,
                new PostFeedSummaryLoader(
                        postContentRepository,
                        commentContentRepository,
                        tagContentRepository,
                        postContentBlockRepository,
                        postSummaryCache,
                        postContentBlockTextProjector,
                        postSummaryAssembler
                ),
                followFeedCursorCodec
        );
    }

    public FeedPageResult listFollowFeed(UUID currentUserId, String cursor, int size) {
        if (currentUserId == null) {
            throw new BusinessException(UNAUTHORIZED, "未获取到认证信息");
        }
        FollowFeedCursorCodec.CursorState state = followFeedCursorCodec.decode(cursor);
        int requestedSize = normalizeRequestedSize(state.size() > 0 ? state.size() : size);
        FollowFeedCache.FollowFeedPageSlice pageSlice = followFeedCache.getOrLoadPage(
                currentUserId,
                state.normalizedCursor(),
                requestedSize,
                () -> loadFollowFeedPage(currentUserId, state, requestedSize)
        );
        List<UUID> pageIds = pageSlice.ids();
        boolean hasNext = pageIds.size() > requestedSize
                && pageSlice.anchorCreateTime() != null
                && pageSlice.anchorPostId() != null;
        List<UUID> renderIds = hasNext ? pageIds.subList(0, requestedSize) : pageIds;
        List<PostSummaryResult> items = renderIds.isEmpty()
                ? List.of()
                : postFeedSummaryLoader.readSummaries(renderIds);
        String nextCursor = hasNext
                ? followFeedCursorCodec.encode(
                        requestedSize,
                        pageSlice.anchorCreateTime().getTime(),
                        pageSlice.anchorPostId()
                )
                : "";
        return new FeedPageResult(items, nextCursor, RANK_VERSION);
    }

    private FollowFeedCache.FollowFeedPageSlice loadFollowFeedPage(
            UUID currentUserId,
            FollowFeedCursorCodec.CursorState state,
            int requestedSize
    ) {
        List<UUID> authorIds = followQueryApi.listFolloweeIds(currentUserId, FOLLOWEE_LIMIT);
        if (authorIds == null || authorIds.isEmpty()) {
            return new FollowFeedCache.FollowFeedPageSlice(List.of(), null, null);
        }
        int fetchLimit = requestedSize + 1;
        List<DiscussPost> rows = state.hasAnchor()
                ? postContentRepository.listRecentVisiblePostsByAuthorIdsBefore(
                        authorIds,
                        new Date(state.anchorCreateTimeMillis()),
                        state.anchorPostId(),
                        fetchLimit
                )
                : postContentRepository.listRecentVisiblePostsByAuthorIds(authorIds, fetchLimit);
        List<DiscussPost> candidates = (rows == null ? List.<DiscussPost>of() : rows).stream()
                .filter(post -> post != null && post.getId() != null)
                .toList();
        if (candidates.isEmpty()) {
            return new FollowFeedCache.FollowFeedPageSlice(List.of(), null, null);
        }
        boolean hasNext = candidates.size() > requestedSize;
        List<DiscussPost> cachedPosts = candidates.subList(0, Math.min(fetchLimit, candidates.size()));
        Date anchorCreateTime = null;
        UUID anchorPostId = null;
        if (hasNext) {
            DiscussPost anchorPost = candidates.get(requestedSize - 1);
            anchorCreateTime = anchorPost.getCreateTime();
            anchorPostId = anchorPost.getId();
        }
        return new FollowFeedCache.FollowFeedPageSlice(
                cachedPosts.stream().map(DiscussPost::getId).toList(),
                anchorCreateTime,
                anchorPostId
        );
    }

    private int normalizeRequestedSize(int size) {
        return Math.min(MAX_SIZE, Math.max(1, size <= 0 ? DEFAULT_SIZE : size));
    }
}
