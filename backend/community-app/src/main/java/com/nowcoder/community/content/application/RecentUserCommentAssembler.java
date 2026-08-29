package com.nowcoder.community.content.application;

import com.nowcoder.community.common.constants.EntityTypes;
import com.nowcoder.community.content.api.query.PostReadQueryApi.RecentUserCommentView;
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

    public RecentUserCommentView assemble(Comment comment, UUID postId, String postTitle) {
        return new RecentUserCommentView(
                comment.getId(),
                comment.getUserId(),
                comment.isRootComment() ? EntityTypes.POST : EntityTypes.COMMENT,
                comment.isRootComment() ? postId : comment.getParentCommentId(),
                comment.getReplyToUserId(),
                postId,
                textCodec.decodeOnRead(postTitle),
                textCodec.decodeOnRead(comment.getContent()),
                comment.getCreateTime()
        );
    }
}
