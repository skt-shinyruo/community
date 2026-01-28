package com.nowcoder.community.content.service;

// 评论并发写入测试：验证 comment_count 原子增量不会丢更新。
import com.nowcoder.community.content.dao.DiscussPostMapper;
import com.nowcoder.community.content.entity.DiscussPost;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.Date;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

@SpringBootTest
class CommentConcurrencyTest {

    @Autowired
    CommentService commentService;

    @Autowired
    PostService postService;

    @Autowired
    DiscussPostMapper discussPostMapper;

    @MockBean
    UserModerationClient userModerationClient;

    @MockBean
    SocialBlockClient socialBlockClient;

    @BeforeEach
    void stubClients() {
        UserModerationClient.ModerationStatus status = new UserModerationClient.ModerationStatus();
        status.setUserId(1);
        status.setMuteUntil(null);
        status.setBanUntil(null);
        when(userModerationClient.getStatus(anyInt())).thenReturn(status);
        doNothing().when(socialBlockClient).assertNotBlocked(anyInt(), anyInt());
    }

    @Test
    void shouldIncrementCommentCountAtomically() throws Exception {
        DiscussPost post = new DiscussPost();
        post.setUserId(1);
        post.setCategoryId(1);
        post.setTitle("concurrency");
        post.setContent("comment count");
        post.setType(0);
        post.setStatus(0);
        post.setCreateTime(new Date());
        post.setCommentCount(0);
        post.setScore(0);
        int postId = postService.create(post);

        int total = 32;
        ExecutorService executor = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(total);
        for (int i = 0; i < total; i++) {
            executor.submit(() -> {
                try {
                    start.await();
                    commentService.addComment(1, postId, CommentService.ENTITY_TYPE_POST, postId, 0, "hi");
                } catch (Exception ignored) {
                    // 测试中忽略异常，最终以计数结果为准
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        done.await(10, TimeUnit.SECONDS);
        executor.shutdownNow();

        DiscussPost updated = discussPostMapper.selectDiscussPostById(postId);
        assertThat(updated.getCommentCount()).isEqualTo(total);
    }
}
