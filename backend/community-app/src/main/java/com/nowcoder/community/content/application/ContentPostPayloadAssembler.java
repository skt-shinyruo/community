package com.nowcoder.community.content.application;

import com.nowcoder.community.content.contracts.event.PostPayload;
import com.nowcoder.community.content.domain.model.DiscussPost;
import com.nowcoder.community.content.domain.model.PostContentBlock;
import com.nowcoder.community.content.domain.repository.PostContentBlockRepository;
import com.nowcoder.community.content.domain.repository.PostContentRepository;
import com.nowcoder.community.content.domain.repository.TagContentRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * PostPayload 组装器（SSOT）：统一从权威数据源构造对外事件 payload，避免多处拼装导致字段漂移。
 */
@Component
public class ContentPostPayloadAssembler {

    private final PostContentRepository postContentPort;
    private final PostContentBlockRepository postContentBlockRepository;
    private final TagContentRepository tagContentPort;
    private final PostContentBlockTextProjector postContentBlockTextProjector;
    private final ContentTextCodec textCodec;

    public ContentPostPayloadAssembler(
            PostContentRepository postContentPort,
            PostContentBlockRepository postContentBlockRepository,
            TagContentRepository tagContentPort,
            PostContentBlockTextProjector postContentBlockTextProjector,
            ContentTextCodec textCodec
    ) {
        this.postContentPort = postContentPort;
        this.postContentBlockRepository = postContentBlockRepository;
        this.tagContentPort = tagContentPort;
        this.postContentBlockTextProjector = postContentBlockTextProjector;
        this.textCodec = textCodec;
    }

    public PostPayload assemble(UUID postId) {
        DiscussPost post = postContentPort.getByIdAllowDeleted(postId);
        List<String> tags = tagContentPort.getTagsByPostIds(List.of(postId)).getOrDefault(postId, List.of());
        List<PostContentBlock> blocks = postContentBlockRepository.listByPostId(postId);
        return assemble(post, tags, blocks);
    }

    public PostPayload assemble(DiscussPost post, List<String> tags, List<PostContentBlock> blocks) {
        if (post == null || post.getId() == null) {
            throw new IllegalArgumentException("post 为空或非法");
        }
        PostPayload payload = new PostPayload();
        payload.setPostId(post.getId());
        payload.setUserId(post.getUserId());
        payload.setCategoryId(post.getCategoryId());
        payload.setTags(tags == null ? List.of() : tags);
        payload.setTitle(textCodec.decodeOnRead(post.getTitle()));
        payload.setContent(textCodec.decodeOnRead(postContentBlockTextProjector.fullText(blocks)));
        payload.setType(post.getType());
        payload.setStatus(post.getStatus());
        payload.setCreateTime(post.getCreateTime() == null ? null : post.getCreateTime().toInstant());
        payload.setUpdateTime(post.getUpdateTime() == null ? null : post.getUpdateTime().toInstant());
        payload.setScore(post.getScore());
        return payload;
    }
}
