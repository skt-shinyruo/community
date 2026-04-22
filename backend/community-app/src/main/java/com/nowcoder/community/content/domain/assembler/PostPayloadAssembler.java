package com.nowcoder.community.content.domain.assembler;

import com.nowcoder.community.content.contracts.event.PostPayload;
import com.nowcoder.community.content.entity.DiscussPost;
import com.nowcoder.community.content.service.PostService;
import com.nowcoder.community.content.service.TagService;
import com.nowcoder.community.content.text.ContentTextCodec;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * PostPayload 组装器（SSOT）：统一从权威数据源构造对外事件 payload，避免多处拼装导致字段漂移。
 */
@Component
public class PostPayloadAssembler {

    private final PostService postService;
    private final TagService tagService;
    private final ContentTextCodec textCodec;

    public PostPayloadAssembler(PostService postService, TagService tagService, ContentTextCodec textCodec) {
        this.postService = postService;
        this.tagService = tagService;
        this.textCodec = textCodec;
    }

    public PostPayload assemble(UUID postId) {
        DiscussPost post = postService.getByIdAllowDeleted(postId);
        List<String> tags = tagService.getTagsByPostIds(List.of(postId)).getOrDefault(postId, List.of());
        return assemble(post, tags);
    }

    public PostPayload assemble(DiscussPost post, List<String> tags) {
        if (post == null || post.getId() == null) {
            throw new IllegalArgumentException("post 为空或非法");
        }
        PostPayload payload = new PostPayload();
        payload.setPostId(post.getId());
        payload.setUserId(post.getUserId());
        payload.setCategoryId(post.getCategoryId());
        payload.setTags(tags == null ? List.of() : tags);
        payload.setTitle(textCodec.decodeOnRead(post.getTitle()));
        payload.setContent(textCodec.decodeOnRead(post.getContent()));
        payload.setType(post.getType());
        payload.setStatus(post.getStatus());
        payload.setCreateTime(post.getCreateTime() == null ? null : post.getCreateTime().toInstant());
        payload.setScore(post.getScore());
        return payload;
    }
}
