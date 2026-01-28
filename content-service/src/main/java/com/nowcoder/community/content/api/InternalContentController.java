package com.nowcoder.community.content.api;

import com.nowcoder.community.common.api.Result;
import com.nowcoder.community.common.event.payload.PostPayload;
import com.nowcoder.community.content.api.dto.InternalPostScanResponse;
import com.nowcoder.community.content.dao.DiscussPostMapper;
import com.nowcoder.community.content.entity.DiscussPost;
import com.nowcoder.community.content.service.TagService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * content-service 内部接口（不走 JWT），通过 X-Internal-Token 做最小权限保护。
 */
@RestController
@RequestMapping("/internal/content")
public class InternalContentController {

    private final DiscussPostMapper discussPostMapper;
    private final TagService tagService;

    public InternalContentController(
            DiscussPostMapper discussPostMapper,
            TagService tagService
    ) {
        this.discussPostMapper = discussPostMapper;
        this.tagService = tagService;
    }

    /**
     * 按游标扫描帖子，供 search-service 重建索引使用。
     *
     * <p>注意：该接口为内部用途，返回字段以索引所需为主（含 title/content）。</p>
     */
    @GetMapping("/posts")
    public Result<InternalPostScanResponse> scanPosts(
            @RequestParam(required = false) Integer afterId,
            @RequestParam(required = false) Integer limit
    ) {
        int a = afterId == null ? 0 : Math.max(0, afterId);
        int l = limit == null ? 500 : Math.min(1000, Math.max(1, limit));

        List<DiscussPost> posts = discussPostMapper.selectDiscussPostsAfterId(a, l);
        List<Integer> postIds = posts.stream().map(DiscussPost::getId).toList();
        var tagsByPostId = tagService.getTagsByPostIds(postIds);
        List<PostPayload> items = posts.stream()
                .map(p -> toPostPayload(p, tagsByPostId.getOrDefault(p.getId(), List.of())))
                .toList();

        InternalPostScanResponse resp = new InternalPostScanResponse();
        resp.setItems(items);

        int nextAfterId = a;
        if (!posts.isEmpty()) {
            nextAfterId = posts.get(posts.size() - 1).getId();
        }
        resp.setNextAfterId(nextAfterId);
        // 约定：当返回数量达到 limit 时，认为“可能还有更多”，由调用方继续拉取直到 items 为空。
        resp.setHasMore(posts.size() == l);

        return Result.ok(resp);
    }

    private PostPayload toPostPayload(DiscussPost post, List<String> tags) {
        PostPayload payload = new PostPayload();
        payload.setPostId(post.getId());
        payload.setUserId(post.getUserId());
        payload.setCategoryId(post.getCategoryId());
        payload.setTags(tags);
        payload.setTitle(post.getTitle());
        payload.setContent(post.getContent());
        payload.setType(post.getType());
        payload.setStatus(post.getStatus());
        payload.setCreateTime(post.getCreateTime() == null ? null : post.getCreateTime().toInstant());
        payload.setScore(post.getScore());
        return payload;
    }
}
