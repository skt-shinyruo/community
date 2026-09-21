package com.nowcoder.community.content.application;

import com.nowcoder.community.common.constants.EntityTypes;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.content.application.result.CommentPageResult;
import com.nowcoder.community.content.application.result.CommentResult;
import com.nowcoder.community.content.domain.model.Comment;
import com.nowcoder.community.content.domain.repository.CommentContentRepository;
import com.nowcoder.community.content.domain.repository.PostContentRepository;
import com.nowcoder.community.social.api.query.SocialLikeQueryApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

@Service
public class CommentReadApplicationService {

    private static final Logger log = LoggerFactory.getLogger(CommentReadApplicationService.class);

    private static final int DEFAULT_SIZE = 10;
    private static final int MAX_SIZE = 50;
    // 热度排序候选窗口：每页固定取该帖最新的这么多条根评论参与排序。
    // ponytail: 更旧的高赞评论不进热度视图；评论数长期超窗时改为 SQL join social_like 分页或点赞数快照列。
    private static final int HOT_CANDIDATE_LIMIT = 200;

    private final CommentContentRepository commentContentPort;
    private final PostContentRepository postContentPort;
    private final SocialLikeQueryApi likeQueryApi;
    private final ContentTextCodec textCodec;
    private final CommentCursorCodec commentCursorCodec;
    private final CommentPageCache commentPageCache;

    public CommentReadApplicationService(
            CommentContentRepository commentContentPort,
            PostContentRepository postContentPort,
            SocialLikeQueryApi likeQueryApi,
            ContentTextCodec textCodec,
            CommentCursorCodec commentCursorCodec,
            CommentPageCache commentPageCache
    ) {
        this.commentContentPort = commentContentPort;
        this.postContentPort = postContentPort;
        this.likeQueryApi = likeQueryApi;
        this.textCodec = textCodec;
        this.commentCursorCodec = commentCursorCodec;
        this.commentPageCache = commentPageCache;
    }

    public CommentPageResult listRootComments(UUID postId, String sort, String cursor, Integer size) {
        CommentSort commentSort = CommentSort.resolve(sort);
        String safeCursor = normalizeCursor(cursor);
        int requestedSize = requestedSize(size);
        Optional<CommentCursorCodec.Boundary> boundary =
                commentCursorCodec.decodeRoot(safeCursor, postId, commentSort);
        assertPostReadable(postId);
        if (safeCursor.isEmpty()) {
            CommentPageResult cached = commentPageCache.getRootPage(postId, commentSort, "", requestedSize);
            if (cached != null) {
                return cached;
            }
        }

        CommentPageResult result;
        if (commentSort == CommentSort.HOT) {
            result = listHotRootComments(postId, boundary, requestedSize);
        } else {
            result = listTimeOrderedRootComments(postId, commentSort, boundary, requestedSize);
        }
        if (safeCursor.isEmpty()) {
            commentPageCache.putRootPage(postId, commentSort, "", requestedSize, result);
        }
        return result;
    }

    private CommentPageResult listTimeOrderedRootComments(
            UUID postId,
            CommentSort sort,
            Optional<CommentCursorCodec.Boundary> boundary,
            int requestedSize
    ) {
        List<Comment> rows = commentContentPort.listRootCommentsAfter(
                postId,
                boundary.map(value -> Date.from(value.createTime())).orElse(null),
                boundary.map(CommentCursorCodec.Boundary::commentId).orElse(null),
                sort == CommentSort.EARLIEST
                        ? CommentContentRepository.ROOT_ORDER_EARLIEST
                        : CommentContentRepository.ROOT_ORDER_LATEST,
                requestedSize + 1
        );
        return toPageResult(
                rows,
                requestedSize,
                last -> commentCursorCodec.encodeRoot(
                        postId,
                        sort,
                        0L,
                        last.getCreateTime().toInstant(),
                        last.getId()
                )
        );
    }

    /**
     * 热度排序：likes(owner social) -> time desc -> id desc。
     *
     * <p>每页都取该帖最新的 {@value #HOT_CANDIDATE_LIMIT} 条根评论（固定候选窗口，
     * 不带时间边界——rank 游标负责翻页），在 application 层按赞数重排后，从
     * (likeCount, createTime, commentId) rank 游标处继续截取。窗口固定使翻页
     * 语义稳定：同一份候选集反复排序，不会因时间边界漏行；超出窗口的更旧评论
     * 不参与热度排序（见 ponytail 注释）。</p>
     */
    private CommentPageResult listHotRootComments(
            UUID postId,
            Optional<CommentCursorCodec.Boundary> boundary,
            int requestedSize
    ) {
        List<Comment> rows = commentContentPort.listRootCommentsAfter(
                postId,
                null,
                null,
                CommentContentRepository.ROOT_ORDER_LATEST,
                HOT_CANDIDATE_LIMIT
        );
        if (rows.isEmpty()) {
            return new CommentPageResult(List.of(), "");
        }

        Map<UUID, Long> likeCounts = readCommentLikeCounts(rows);
        Comparator<Comment> hotOrder = Comparator
                .comparingLong((Comment comment) -> likeCounts.getOrDefault(comment.getId(), 0L))
                .reversed()
                .thenComparing(Comment::getCreateTime, Comparator.reverseOrder())
                .thenComparing(Comment::getId, Comparator.reverseOrder());

        List<Comment> candidates = new ArrayList<>(rows);
        candidates.sort(hotOrder);
        if (boundary.isPresent()) {
            CommentCursorCodec.Boundary rank = boundary.get();
            candidates.removeIf(comment -> !isStrictlyBelowHotRank(
                    likeCounts.getOrDefault(comment.getId(), 0L), comment, rank));
        }

        boolean hasNext = candidates.size() > requestedSize;
        List<Comment> pageRows = candidates.stream().limit(requestedSize).toList();
        if (!hasNext) {
            return new CommentPageResult(toResults(pageRows), "");
        }
        Comment last = pageRows.get(pageRows.size() - 1);
        String nextCursor = commentCursorCodec.encodeRoot(
                postId,
                CommentSort.HOT,
                likeCounts.getOrDefault(last.getId(), 0L),
                last.getCreateTime().toInstant(),
                last.getId()
        );
        return new CommentPageResult(toResults(pageRows), nextCursor);
    }

    private Map<UUID, Long> readCommentLikeCounts(List<Comment> rows) {
        List<UUID> commentIds = rows.stream().map(Comment::getId).toList();
        try {
            Map<UUID, Long> counts = likeQueryApi.counts(EntityTypes.COMMENT, commentIds);
            return counts == null ? new HashMap<>() : counts;
        } catch (RuntimeException exception) {
            // 热度排序是读取增强：social 不可用时降级为按时间排序，不让评论读取失败。
            log.warn("[comment-read] hot sort like counts read degraded: {}", exception.toString());
            return new HashMap<>();
        }
    }

    private static boolean isStrictlyBelowHotRank(
            long likeCount,
            Comment comment,
            CommentCursorCodec.Boundary rank
    ) {
        int byCount = Long.compare(likeCount, rank.likeCount());
        if (byCount != 0) {
            return byCount < 0;
        }
        int byTime = comment.getCreateTime().compareTo(Date.from(rank.createTime()));
        if (byTime != 0) {
            return byTime < 0;
        }
        return comment.getId().compareTo(rank.commentId()) < 0;
    }

    public CommentPageResult listReplies(UUID postId, UUID rootCommentId, String cursor, Integer size) {
        String safeCursor = normalizeCursor(cursor);
        int requestedSize = requestedSize(size);
        Optional<CommentCursorCodec.Boundary> boundary =
                commentCursorCodec.decodeReply(safeCursor, postId, rootCommentId);
        commentContentPort.assertCommentBelongsToPost(postId, rootCommentId);
        List<Comment> rows = commentContentPort.listRepliesAfter(
                rootCommentId,
                boundary.map(value -> Date.from(value.createTime())).orElse(null),
                boundary.map(CommentCursorCodec.Boundary::commentId).orElse(null),
                requestedSize + 1
        );
        return toPageResult(
                rows,
                requestedSize,
                last -> commentCursorCodec.encodeReply(
                        postId,
                        rootCommentId,
                        last.getCreateTime().toInstant(),
                        last.getId()
                )
        );
    }

    private CommentPageResult toPageResult(
            List<Comment> rows,
            int size,
            Function<Comment, String> nextCursorEncoder
    ) {
        List<Comment> candidates = rows == null ? List.of() : rows;
        boolean hasNext = candidates.size() > size;
        List<Comment> pageRows = candidates.stream()
                .limit(size)
                .toList();
        String nextCursor = hasNext
                ? nextCursorEncoder.apply(pageRows.get(pageRows.size() - 1))
                : "";
        return new CommentPageResult(toResults(pageRows), nextCursor);
    }

    private List<CommentResult> toResults(List<Comment> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        return rows.stream()
                .map(this::toResult)
                .toList();
    }

    private CommentResult toResult(Comment comment) {
        return new CommentResult(
                comment.getId(),
                comment.getUserId(),
                comment.getPostId(),
                comment.getRootCommentId(),
                comment.getParentCommentId(),
                comment.getReplyToUserId(),
                textCodec.decodeOnRead(comment.getContent()),
                comment.getCreateTime(),
                comment.getUpdateTime(),
                comment.getEditCount()
        );
    }

    private static String normalizeCursor(String cursor) {
        return cursor == null ? "" : cursor.trim();
    }

    private static int requestedSize(Integer size) {
        return Math.min(MAX_SIZE, Math.max(1, size == null ? DEFAULT_SIZE : size));
    }

    private void assertPostReadable(UUID postId) {
        if (postId != null) {
            postContentPort.getById(postId);
        }
    }
}
