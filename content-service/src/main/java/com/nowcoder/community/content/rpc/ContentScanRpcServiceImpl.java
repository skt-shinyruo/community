package com.nowcoder.community.content.rpc;

import com.nowcoder.community.common.api.CommonErrorCode;
import com.nowcoder.community.common.api.ErrorCode;
import com.nowcoder.community.common.api.Result;
import com.nowcoder.community.common.event.payload.PostPayload;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.content.api.rpc.ContentScanRpcService;
import com.nowcoder.community.content.api.rpc.dto.ContentPostScanResponse;
import com.nowcoder.community.content.dao.DiscussPostMapper;
import com.nowcoder.community.content.entity.DiscussPost;
import com.nowcoder.community.content.service.TagService;
import com.nowcoder.community.content.text.ContentTextCodec;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

@DubboService
public class ContentScanRpcServiceImpl implements ContentScanRpcService {

    private final DiscussPostMapper discussPostMapper;
    private final TagService tagService;
    private final ContentTextCodec textCodec;

    public ContentScanRpcServiceImpl(DiscussPostMapper discussPostMapper, TagService tagService, ContentTextCodec textCodec) {
        this.discussPostMapper = discussPostMapper;
        this.tagService = tagService;
        this.textCodec = textCodec;
    }

    @Override
    public Result<ContentPostScanResponse> scanPosts(int afterId, int limit) {
        try {
            int a = Math.max(0, afterId);
            int l = limit <= 0 ? 500 : Math.min(1000, Math.max(1, limit));

            List<DiscussPost> posts = discussPostMapper.selectDiscussPostsAfterId(a, l);
            if (posts == null) {
                posts = List.of();
            }

            List<Integer> postIds = posts.stream().map(DiscussPost::getId).toList();
            Map<Integer, List<String>> tagsByPostId = tagService.getTagsByPostIds(postIds);
            Map<Integer, List<String>> safeTagsByPostId = tagsByPostId == null ? Map.of() : tagsByPostId;

            List<PostPayload> items = posts.stream()
                    .map(p -> toPostPayload(p, safeTagsByPostId.getOrDefault(p.getId(), List.of())))
                    .toList();

            ContentPostScanResponse resp = new ContentPostScanResponse();
            resp.setItems(items);

            int nextAfterId = a;
            if (!posts.isEmpty()) {
                nextAfterId = posts.get(posts.size() - 1).getId();
            }
            resp.setNextAfterId(nextAfterId);
            resp.setHasMore(posts.size() == l);

            return Result.ok(resp);
        } catch (BusinessException e) {
            return error(e);
        } catch (RuntimeException e) {
            return Result.error(CommonErrorCode.INTERNAL_ERROR);
        }
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

    private <T> Result<T> error(BusinessException e) {
        if (e == null) {
            return Result.error(CommonErrorCode.INTERNAL_ERROR);
        }
        ErrorCode ec = e.getErrorCode() == null ? CommonErrorCode.INTERNAL_ERROR : e.getErrorCode();
        String msg = StringUtils.hasText(e.getMessage()) ? e.getMessage() : ec.getMessage();
        return Result.error(ec.getCode(), msg);
    }
}
