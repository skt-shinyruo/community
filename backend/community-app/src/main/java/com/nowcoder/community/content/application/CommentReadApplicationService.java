package com.nowcoder.community.content.application;

import com.nowcoder.community.content.application.result.CommentPageResult;
import com.nowcoder.community.content.application.result.CommentResult;
import com.nowcoder.community.content.domain.model.Comment;
import com.nowcoder.community.content.domain.repository.CommentContentRepository;
import com.nowcoder.community.content.domain.repository.PostContentRepository;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

@Service
public class CommentReadApplicationService {

    private static final int DEFAULT_SIZE = 10;
    private static final int MAX_SIZE = 50;

    private final CommentContentRepository commentContentPort;
    private final PostContentRepository postContentPort;
    private final ContentTextCodec textCodec;
    private final CommentCursorCodec commentCursorCodec;
    private final CommentPageCache commentPageCache;

    public CommentReadApplicationService(
            CommentContentRepository commentContentPort,
            PostContentRepository postContentPort,
            ContentTextCodec textCodec,
            CommentCursorCodec commentCursorCodec,
            CommentPageCache commentPageCache
    ) {
        this.commentContentPort = commentContentPort;
        this.postContentPort = postContentPort;
        this.textCodec = textCodec;
        this.commentCursorCodec = commentCursorCodec;
        this.commentPageCache = commentPageCache;
    }

    public CommentPageResult listRootComments(UUID postId, String cursor, Integer size) {
        String safeCursor = normalizeCursor(cursor);
        int requestedSize = requestedSize(size);
        Optional<CommentCursorCodec.Boundary> boundary = commentCursorCodec.decodeRoot(safeCursor, postId);
        assertPostReadable(postId);
        if (safeCursor.isEmpty()) {
            CommentPageResult cached = commentPageCache.getRootPage(postId, "", requestedSize);
            if (cached != null) {
                return cached;
            }
        }

        List<Comment> rows = commentContentPort.listRootCommentsAfter(
                postId,
                boundary.map(value -> Date.from(value.createTime())).orElse(null),
                boundary.map(CommentCursorCodec.Boundary::commentId).orElse(null),
                requestedSize + 1
        );
        CommentPageResult result = toPageResult(
                rows,
                requestedSize,
                last -> commentCursorCodec.encodeRoot(
                        postId,
                        last.getCreateTime().toInstant(),
                        last.getId()
                )
        );
        if (safeCursor.isEmpty()) {
            commentPageCache.putRootPage(postId, "", requestedSize, result);
        }
        return result;
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

    public List<CommentResult> comments(UUID postId, Integer page, Integer size) {
        int p = Math.max(0, page == null ? 0 : page);
        int s = requestedSize(size);
        assertPostReadable(postId);
        return toResults(commentContentPort.listRootComments(postId, p, s));
    }

    public List<CommentResult> replies(UUID postId, UUID rootCommentId, Integer page, Integer size) {
        int p = Math.max(0, page == null ? 0 : page);
        int s = requestedSize(size);
        commentContentPort.assertCommentBelongsToPost(postId, rootCommentId);
        return toResults(commentContentPort.listReplies(rootCommentId, p, s));
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
