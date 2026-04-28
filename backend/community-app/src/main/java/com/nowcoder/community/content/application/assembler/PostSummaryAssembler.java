package com.nowcoder.community.content.application.assembler;

import com.nowcoder.community.content.application.result.PostSummaryResult;
import com.nowcoder.community.content.domain.model.Comment;
import com.nowcoder.community.content.domain.model.DiscussPost;
import com.nowcoder.community.content.application.ContentTextCodec;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.UUID;

@Component
public class PostSummaryAssembler {

    private final ContentTextCodec textCodec;

    public PostSummaryAssembler(ContentTextCodec textCodec) {
        this.textCodec = textCodec;
    }

    public PostSummaryResult assemble(DiscussPost post, Comment lastActivity, List<String> tags) {
        List<String> safeTags = tags == null ? List.of() : List.copyOf(tags);
        UUID lastReplyUserId = null;
        Date lastReplyTime = null;
        String lastReplyPreview = null;
        Date lastActivityTime = post == null ? null : post.getCreateTime();

        if (lastActivity != null && lastActivity.getUserId() != null && lastActivity.getCreateTime() != null) {
            lastReplyUserId = lastActivity.getUserId();
            lastReplyTime = lastActivity.getCreateTime();
            lastReplyPreview = textCodec.decodeOnRead(lastActivity.getContent());
            if (lastActivityTime == null || lastReplyTime.after(lastActivityTime)) {
                lastActivityTime = lastReplyTime;
            }
        }

        return new PostSummaryResult(
                post.getId(),
                post.getUserId(),
                textCodec.decodeOnRead(post.getTitle()),
                post.getType(),
                post.getStatus(),
                post.getCreateTime(),
                post.getCommentCount(),
                post.getScore(),
                post.getCategoryId(),
                safeTags,
                lastReplyUserId,
                lastReplyTime,
                lastActivityTime,
                lastReplyPreview
        );
    }
}
