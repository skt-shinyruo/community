package com.nowcoder.community.content.service;

import com.nowcoder.community.content.event.payload.PostPayload;
import com.nowcoder.community.content.dto.PostScanResult;
import com.nowcoder.community.content.mapper.DiscussPostMapper;
import com.nowcoder.community.content.entity.DiscussPost;
import com.nowcoder.community.content.text.ContentTextCodec;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class PostScanService {

    private final DiscussPostMapper discussPostMapper;
    private final TagService tagService;
    private final ContentTextCodec textCodec;

    public PostScanService(DiscussPostMapper discussPostMapper, TagService tagService, ContentTextCodec textCodec) {
        this.discussPostMapper = discussPostMapper;
        this.tagService = tagService;
        this.textCodec = textCodec;
    }

    public PostScanResult scanPosts(int afterId, int limit) {
        int safeAfterId = Math.max(0, afterId);
        int safeLimit = limit <= 0 ? 500 : Math.min(1000, Math.max(1, limit));

        List<DiscussPost> posts = discussPostMapper.selectDiscussPostsAfterId(safeAfterId, safeLimit);
        if (posts == null) {
            posts = List.of();
        }

        List<Integer> postIds = posts.stream().map(DiscussPost::getId).toList();
        Map<Integer, List<String>> tagsByPostId = tagService.getTagsByPostIds(postIds);
        Map<Integer, List<String>> safeTagsByPostId = tagsByPostId == null ? Map.of() : tagsByPostId;

        List<PostPayload> items = posts.stream()
                .map(post -> toPostPayload(post, safeTagsByPostId.getOrDefault(post.getId(), List.of())))
                .toList();

        PostScanResult response = new PostScanResult();
        response.setItems(items);

        int nextAfterId = safeAfterId;
        if (!posts.isEmpty()) {
            nextAfterId = posts.get(posts.size() - 1).getId();
        }
        response.setNextAfterId(nextAfterId);
        response.setHasMore(posts.size() == safeLimit);
        return response;
    }

    /**
     * Loads a single post snapshot for projection purposes.
     *
     * <p>Returns {@code null} when the post does not exist.</p>
     */
    public PostPayload getPostPayloadAllowDeleted(int postId) {
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
        return toPostPayload(post, tags);
    }

    private PostPayload toPostPayload(DiscussPost post, List<String> tags) {
        PostPayload payload = new PostPayload();
        payload.setPostId(post.getId());
        payload.setUserId(post.getUserId());
        payload.setCategoryId(post.getCategoryId());
        payload.setTags(tags);
        payload.setTitle(textCodec.decodeOnRead(post.getTitle()));
        payload.setContent(textCodec.decodeOnRead(post.getContent()));
        payload.setType(post.getType());
        payload.setStatus(post.getStatus());
        payload.setCreateTime(post.getCreateTime() == null ? null : post.getCreateTime().toInstant());
        payload.setScore(post.getScore());
        return payload;
    }
}
