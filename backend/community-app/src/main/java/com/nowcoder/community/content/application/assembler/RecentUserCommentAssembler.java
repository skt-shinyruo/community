package com.nowcoder.community.content.application.assembler;

import com.nowcoder.community.content.application.result.RecentUserCommentResult;
import com.nowcoder.community.content.domain.model.Comment;
import com.nowcoder.community.content.application.ContentTextCodec;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class RecentUserCommentAssembler {

    private final ContentTextCodec textCodec;

    public RecentUserCommentAssembler(ContentTextCodec textCodec) {
        this.textCodec = textCodec;
    }

    public RecentUserCommentResult assemble(Comment comment, UUID postId, String postTitle) {
        return new RecentUserCommentResult(
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
