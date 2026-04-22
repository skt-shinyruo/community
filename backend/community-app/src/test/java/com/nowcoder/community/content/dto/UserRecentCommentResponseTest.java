package com.nowcoder.community.content.dto;

import com.nowcoder.community.content.entity.Comment;
import com.nowcoder.community.content.entity.DiscussPost;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;

class UserRecentCommentResponseTest {

    @Test
    void fromShouldAssembleReadableCommentActivity() {
        UUID commentId = uuid(11);
        UUID userId = uuid(7);
        UUID entityId = uuid(5);
        UUID targetId = uuid(9);
        UUID postId = uuid(101);
        Comment comment = new Comment();
        comment.setId(commentId);
        comment.setUserId(userId);
        comment.setEntityType(2);
        comment.setEntityId(entityId);
        comment.setTargetId(targetId);
        comment.setContent("<reply>");
        comment.setCreateTime(new Date());

        DiscussPost post = new DiscussPost();
        post.setId(postId);
        post.setTitle("<title>");

        UserRecentCommentResponse response = UserRecentCommentResponse.from(comment, postId, "<title>");

        assertThat(response.getId()).isEqualTo(commentId);
        assertThat(response.getUserId()).isEqualTo(userId);
        assertThat(response.getEntityType()).isEqualTo(2);
        assertThat(response.getEntityId()).isEqualTo(entityId);
        assertThat(response.getPostId()).isEqualTo(postId);
        assertThat(response.getPostTitle()).isEqualTo("<title>");
        assertThat(response.getContent()).isEqualTo("<reply>");
    }
}
