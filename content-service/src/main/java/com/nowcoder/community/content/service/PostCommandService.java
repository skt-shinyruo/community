package com.nowcoder.community.content.service;

import com.nowcoder.community.common.event.payload.PostPayload;
import com.nowcoder.community.common.tx.AfterCommitExecutor;
import com.nowcoder.community.content.entity.DiscussPost;
import com.nowcoder.community.content.event.ContentEventPublisher;
import com.nowcoder.community.content.score.PostScoreQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

/**
 * 帖子写路径命令服务：
 * - 聚合“写库 + 发布事件 + 非 DB 副作用”（热度刷新队列）到同一事务域
 * - 通过 After-Commit 将副作用延后到事务提交后执行，避免回滚导致的幽灵行为
 */
@Service
public class PostCommandService {

    private static final Logger log = LoggerFactory.getLogger(PostCommandService.class);

    private final PostService postService;
    private final PostScoreQueue postScoreQueue;
    private final ContentEventPublisher eventPublisher;
    private final CategoryService categoryService;
    private final TagService tagService;

    public PostCommandService(
            PostService postService,
            PostScoreQueue postScoreQueue,
            ContentEventPublisher eventPublisher,
            CategoryService categoryService,
            TagService tagService
    ) {
        this.postService = postService;
        this.postScoreQueue = postScoreQueue;
        this.eventPublisher = eventPublisher;
        this.categoryService = categoryService;
        this.tagService = tagService;
    }

    @Transactional
    public int createPost(int userId, String title, String content, Integer categoryId, List<String> tags) {
        categoryService.assertExists(categoryId);

        DiscussPost post = new DiscussPost();
        post.setUserId(userId);
        post.setCategoryId(categoryId);
        post.setTitle(title);
        post.setContent(content);
        post.setType(0);
        post.setStatus(0);
        post.setCreateTime(new Date());
        post.setCommentCount(0);
        post.setScore(0.0);

        int postId = postService.create(post);

        // taxonomy：绑定 tags（同事务写入，便于后续列表/侧栏聚合使用）
        List<String> normalizedTags = tagService.bindTagsToPost(postId, tags);

        PostPayload payload = new PostPayload();
        payload.setPostId(postId);
        payload.setUserId(userId);
        payload.setCategoryId(categoryId);
        payload.setTags(normalizedTags);
        payload.setTitle(title);
        payload.setContent(content);
        payload.setType(post.getType());
        payload.setStatus(post.getStatus());
        payload.setCreateTime(post.getCreateTime() == null ? null : post.getCreateTime().toInstant());
        payload.setScore(post.getScore());

        eventPublisher.publishPostPublished(payload);

        // 热度刷新属于非 DB 副作用：延后到事务提交后，避免回滚仍触发刷新。
        AfterCommitExecutor.runAfterCommit(() -> {
            try {
                postScoreQueue.add(postId);
            } catch (Exception e) {
                log.warn("[post-score] enqueue failed after commit (postId={}): {}", postId, e.toString());
            }
        });

        return postId;
    }
}
