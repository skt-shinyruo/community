package com.nowcoder.community.content.application;

import com.nowcoder.community.content.domain.repository.CommentContentRepository;
import com.nowcoder.community.content.application.result.CommentPageResult;
import com.nowcoder.community.content.application.result.CommentResult;
import com.nowcoder.community.content.domain.model.Comment;
import com.nowcoder.community.content.application.ContentTextCodec;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class CommentReadApplicationService {

    private final CommentContentRepository commentContentPort;
    private final ContentTextCodec textCodec;
    private final FeedCursorCodec feedCursorCodec;

    public CommentReadApplicationService(
            CommentContentRepository commentContentPort,
            ContentTextCodec textCodec,
            FeedCursorCodec feedCursorCodec
    ) {
        this.commentContentPort = commentContentPort;
        this.textCodec = textCodec;
        this.feedCursorCodec = feedCursorCodec;
    }

    public CommentPageResult listRootComments(UUID postId, String cursor, Integer size) {
        FeedCursorCodec.CursorState state = feedCursorCodec.decode(cursor);
        int p = state.page();
        int s = state.size() > 0 ? state.size() : (size == null ? 10 : size);
        List<Comment> rows = commentContentPort.listRootComments(postId, p, s);
        List<CommentResult> items = rows == null ? List.of() : rows.stream().map(this::toResult).toList();
        return new CommentPageResult(items, nextCursor(cursor, p, s, items.size()));
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
}
