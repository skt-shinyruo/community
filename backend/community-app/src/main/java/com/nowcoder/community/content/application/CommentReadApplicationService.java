package com.nowcoder.community.content.application;

import com.nowcoder.community.content.domain.repository.CommentContentRepository;
import com.nowcoder.community.content.application.result.CommentPageResult;
import com.nowcoder.community.content.application.result.CommentResult;
import com.nowcoder.community.content.domain.model.Comment;
import com.nowcoder.community.content.application.ContentTextCodec;
import com.nowcoder.community.content.domain.repository.PostContentRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class CommentReadApplicationService {

    private final CommentContentRepository commentContentPort;
    private final PostContentRepository postContentPort;
    private final ContentTextCodec textCodec;
    private final FeedCursorCodec feedCursorCodec;
    private final CommentPageCache commentPageCache;

    public CommentReadApplicationService(
            CommentContentRepository commentContentPort,
            PostContentRepository postContentPort,
            ContentTextCodec textCodec,
            FeedCursorCodec feedCursorCodec,
            CommentPageCache commentPageCache
    ) {
        this.commentContentPort = commentContentPort;
        this.postContentPort = postContentPort;
        this.textCodec = textCodec;
        this.feedCursorCodec = feedCursorCodec;
        this.commentPageCache = commentPageCache;
    }

    public CommentPageResult listRootComments(UUID postId, String cursor, Integer size) {
        assertPostReadable(postId);
        String safeCursor = normalizeCursor(cursor);
        int requestedSize = requestedSize(size);
        if (safeCursor.isEmpty()) {
            CommentPageResult cached = commentPageCache.getRootPage(postId, "", requestedSize);
            if (cached != null) {
                return cached;
            }
        }

        FeedCursorCodec.CursorState state = feedCursorCodec.decode(cursor);
        int p = state.page();
        int s = state.size() > 0 ? state.size() : requestedSize;
        List<Comment> rows = commentContentPort.listRootComments(postId, p, s);
        List<CommentResult> items = rows == null ? List.of() : rows.stream().map(this::toResult).toList();
        CommentPageResult result = new CommentPageResult(items, nextCursor(cursor, p, s, items.size()));
        if (safeCursor.isEmpty() && p == 0) {
            commentPageCache.putRootPage(postId, "", s, result);
        }
        return result;
    }

    public CommentPageResult listReplies(UUID postId, UUID rootCommentId, String cursor, Integer size) {
        commentContentPort.assertCommentBelongsToPost(postId, rootCommentId);
        FeedCursorCodec.CursorState state = feedCursorCodec.decode(cursor);
        int p = state.page();
        int s = state.size() > 0 ? state.size() : (size == null ? 10 : size);
        List<Comment> rows = commentContentPort.listReplies(rootCommentId, p, s);
        List<CommentResult> items = rows == null ? List.of() : rows.stream().map(this::toResult).toList();
        return new CommentPageResult(items, nextCursor(cursor, p, s, items.size()));
    }

    public List<CommentResult> comments(UUID postId, Integer page, Integer size) {
        int p = page == null ? 0 : page;
        int s = size == null ? 10 : size;
        return listRootComments(postId, feedCursorCodec.encodePage(p, s), s).items();
    }

    public List<CommentResult> replies(UUID postId, UUID rootCommentId, Integer page, Integer size) {
        int p = page == null ? 0 : page;
        int s = size == null ? 10 : size;
        return listReplies(postId, rootCommentId, feedCursorCodec.encodePage(p, s), s).items();
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

    private String nextCursor(String cursor, int page, int size, int actualSize) {
        if (actualSize <= 0) {
            return "";
        }
        return feedCursorCodec.encodePage(page + 1, size);
    }

    private static String normalizeCursor(String cursor) {
        return cursor == null ? "" : cursor.trim();
    }

    private static int requestedSize(Integer size) {
        return Math.max(1, size == null ? 10 : size);
    }

    private void assertPostReadable(UUID postId) {
        if (postId != null) {
            postContentPort.getById(postId);
        }
    }
}
