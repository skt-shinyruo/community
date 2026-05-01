package com.nowcoder.community.content.application;

import com.nowcoder.community.content.application.result.PostDetailResult;
import com.nowcoder.community.content.domain.model.DiscussPost;
import com.nowcoder.community.content.application.ContentTextCodec;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PostDetailAssembler {

    private final ContentTextCodec textCodec;

    public PostDetailAssembler(ContentTextCodec textCodec) {
        this.textCodec = textCodec;
    }

    public PostDetailResult assemble(DiscussPost post, List<String> tags, long likeCount, boolean liked, boolean bookmarked) {
        List<String> safeTags = tags == null ? List.of() : List.copyOf(tags);
        return new PostDetailResult(
                post.getId(),
                post.getUserId(),
                textCodec.decodeOnRead(post.getTitle()),
                textCodec.decodeOnRead(post.getContent()),
                post.getType(),
                post.getStatus(),
                post.getCreateTime(),
                post.getUpdateTime(),
                post.getEditCount(),
                post.getCommentCount(),
                post.getScore(),
                post.getCategoryId(),
                safeTags,
                likeCount,
                liked,
                bookmarked
        );
    }
}
