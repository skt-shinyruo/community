package com.nowcoder.community.content.application;

import com.nowcoder.community.content.api.event.payload.PostPayload;
import com.nowcoder.community.content.application.dto.PostScanResult;
import com.nowcoder.community.content.dao.DiscussPostMapper;
import com.nowcoder.community.content.entity.DiscussPost;
import com.nowcoder.community.content.service.TagService;
import com.nowcoder.community.content.text.ContentTextCodec;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class PostScanApplicationService {

    private final DiscussPostMapper discussPostMapper;
    private final TagService tagService;
    private final ContentTextCodec textCodec;

    public PostScanApplicationService(DiscussPostMapper discussPostMapper, TagService tagService, ContentTextCodec textCodec) {
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
