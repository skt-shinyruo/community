package com.nowcoder.community.content.assembler;

import com.nowcoder.community.content.api.model.PostDetailView;
import com.nowcoder.community.content.entity.DiscussPost;
import com.nowcoder.community.content.text.ContentTextCodec;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PostDetailAssembler {

    private final ContentTextCodec textCodec;

    public PostDetailAssembler(ContentTextCodec textCodec) {
        this.textCodec = textCodec;
    }

    public PostDetailView assemble(DiscussPost post, List<String> tags, long likeCount, boolean liked, boolean bookmarked) {
        List<String> safeTags = tags == null ? List.of() : List.copyOf(tags);
        return new PostDetailView(
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
