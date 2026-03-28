package com.nowcoder.community.content.assembler;

import com.nowcoder.community.content.api.model.RecentUserCommentView;
import com.nowcoder.community.content.entity.Comment;
import com.nowcoder.community.content.text.ContentTextCodec;
import org.springframework.stereotype.Component;

@Component
public class RecentUserCommentAssembler {

    private final ContentTextCodec textCodec;

    public RecentUserCommentAssembler(ContentTextCodec textCodec) {
        this.textCodec = textCodec;
    }

    public RecentUserCommentView assemble(Comment comment, int postId, String postTitle) {
        return new RecentUserCommentView(
                comment.getId(),
                comment.getUserId(),
                comment.getEntityType(),
                comment.getEntityId(),
                comment.getTargetId(),
                postId,
                textCodec.decodeOnRead(postTitle),
                textCodec.decodeOnRead(comment.getContent()),
                comment.getCreateTime()
        );
    }
}
