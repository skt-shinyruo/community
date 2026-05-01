package com.nowcoder.community.content.infrastructure.api;

import com.nowcoder.community.content.api.model.PostScanView;
import com.nowcoder.community.content.api.query.PostScanQueryApi;
import com.nowcoder.community.content.domain.repository.TagContentRepository;
import com.nowcoder.community.content.domain.model.DiscussPost;
import com.nowcoder.community.content.infrastructure.persistence.mapper.DiscussPostMapper;
import com.nowcoder.community.content.application.ContentTextCodec;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class PostScanService implements PostScanQueryApi {

    private final DiscussPostMapper discussPostMapper;
    private final TagContentRepository tagContentPort;
    private final ContentTextCodec textCodec;

    public PostScanService(DiscussPostMapper discussPostMapper, TagContentRepository tagContentPort, ContentTextCodec textCodec) {
        this.discussPostMapper = discussPostMapper;
        this.tagContentPort = tagContentPort;
        this.textCodec = textCodec;
    }

    @Override
    public PostScanView scanPosts(UUID afterId, int limit) {
        int safeLimit = limit <= 0 ? 500 : Math.min(1000, Math.max(1, limit));

        List<DiscussPost> posts = discussPostMapper.selectDiscussPostsAfterId(afterId, safeLimit);
        if (posts == null) {
            posts = List.of();
        }

        List<UUID> postIds = posts.stream().map(DiscussPost::getId).toList();
        Map<UUID, List<String>> tagsByPostId = tagContentPort.getTagsByPostIds(postIds);
        Map<UUID, List<String>> safeTagsByPostId = tagsByPostId == null ? Map.of() : tagsByPostId;

        List<PostScanView.PostProjectionView> items = posts.stream()
                .map(post -> toPostProjectionView(post, safeTagsByPostId.getOrDefault(post.getId(), List.of())))
                .toList();

        UUID nextAfterId = afterId;
        if (!posts.isEmpty()) {
            nextAfterId = posts.get(posts.size() - 1).getId();
        }
        return new PostScanView(items, nextAfterId, posts.size() == safeLimit);
    }

    /**
     * Loads a single post snapshot for projection purposes.
     *
     * <p>Returns {@code null} when the post does not exist.</p>
     */
    @Override
    public PostScanView.PostProjectionView getPostProjectionAllowDeleted(UUID postId) {
        if (postId == null) {
            return null;
        }

        DiscussPost post = discussPostMapper.selectDiscussPostById(postId);
        if (post == null || post.getId() == null) {
            return null;
        }

        Map<UUID, List<String>> tagsByPostId = tagContentPort.getTagsByPostIds(List.of(postId));
        List<String> tags = tagsByPostId == null ? List.of() : tagsByPostId.getOrDefault(postId, List.of());
        return toPostProjectionView(post, tags);
    }

    private PostScanView.PostProjectionView toPostProjectionView(DiscussPost post, List<String> tags) {
        return new PostScanView.PostProjectionView(
                post.getId(),
                post.getUserId(),
                post.getCategoryId(),
                tags,
                textCodec.decodeOnRead(post.getTitle()),
                textCodec.decodeOnRead(post.getContent()),
                post.getType(),
                post.getStatus(),
                post.getCreateTime() == null ? null : post.getCreateTime().toInstant(),
                post.getScore()
        );
    }
}
