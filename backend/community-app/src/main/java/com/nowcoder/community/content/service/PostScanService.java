package com.nowcoder.community.content.service;

import com.nowcoder.community.content.api.model.PostScanView;
import com.nowcoder.community.content.api.query.PostScanQueryApi;
import com.nowcoder.community.content.entity.DiscussPost;
import com.nowcoder.community.content.mapper.DiscussPostMapper;
import com.nowcoder.community.content.text.ContentTextCodec;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class PostScanService implements PostScanQueryApi {

    private final DiscussPostMapper discussPostMapper;
    private final TagService tagService;
    private final ContentTextCodec textCodec;

    public PostScanService(DiscussPostMapper discussPostMapper, TagService tagService, ContentTextCodec textCodec) {
        this.discussPostMapper = discussPostMapper;
        this.tagService = tagService;
        this.textCodec = textCodec;
    }

    @Override
    public PostScanView scanPosts(int afterId, int limit) {
        int safeAfterId = Math.max(0, afterId);
        int safeLimit = limit <= 0 ? 500 : Math.min(1000, Math.max(1, limit));

        List<DiscussPost> posts = discussPostMapper.selectDiscussPostsAfterId(safeAfterId, safeLimit);
        if (posts == null) {
            posts = List.of();
        }

        List<Integer> postIds = posts.stream().map(DiscussPost::getId).toList();
        Map<Integer, List<String>> tagsByPostId = tagService.getTagsByPostIds(postIds);
        Map<Integer, List<String>> safeTagsByPostId = tagsByPostId == null ? Map.of() : tagsByPostId;

        List<PostScanView.PostProjectionView> items = posts.stream()
                .map(post -> toPostProjectionView(post, safeTagsByPostId.getOrDefault(post.getId(), List.of())))
                .toList();

        int nextAfterId = safeAfterId;
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
    public PostScanView.PostProjectionView getPostProjectionAllowDeleted(int postId) {
        int pid = Math.max(0, postId);
        if (pid <= 0) {
            return null;
        }

        DiscussPost post = discussPostMapper.selectDiscussPostById(pid);
        if (post == null || post.getId() <= 0) {
            return null;
        }

        Map<Integer, List<String>> tagsByPostId = tagService.getTagsByPostIds(List.of(pid));
        List<String> tags = tagsByPostId == null ? List.of() : tagsByPostId.getOrDefault(pid, List.of());
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
