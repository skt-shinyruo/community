package com.nowcoder.community.content.dto;

import com.nowcoder.community.content.entity.Comment;
import com.nowcoder.community.content.entity.DiscussPost;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class UserRecentCommentResponseTest {

    @Test
    void fromShouldAssembleReadableCommentActivity() {
        Comment comment = new Comment();
        comment.setId(11);
        comment.setUserId(7);
        comment.setEntityType(2);
        comment.setEntityId(5);
        comment.setTargetId(9);
        comment.setContent("<reply>");
        comment.setCreateTime(new Date());

        DiscussPost post = new DiscussPost();
        post.setId(101);
        post.setTitle("<title>");

        UserRecentCommentResponse response = UserRecentCommentResponse.from(comment, 101, "<title>");

        assertThat(response.getId()).isEqualTo(11);
        assertThat(response.getUserId()).isEqualTo(7);
        assertThat(response.getEntityType()).isEqualTo(2);
        assertThat(response.getEntityId()).isEqualTo(5);
        assertThat(response.getPostId()).isEqualTo(101);
        assertThat(response.getPostTitle()).isEqualTo("<title>");
        assertThat(response.getContent()).isEqualTo("<reply>");
    }
}
