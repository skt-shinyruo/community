package com.nowcoder.community.content.controller.dto;

import com.nowcoder.community.content.application.result.RecentUserCommentResult;
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
        RecentUserCommentResult view = new RecentUserCommentResult(
                commentId,
                userId,
                2,
                entityId,
                targetId,
                postId,
                "<title>",
                "<reply>",
                new Date()
        );

        UserRecentCommentResponse response = UserRecentCommentResponse.from(view);

        assertThat(response.getId()).isEqualTo(commentId);
        assertThat(response.getUserId()).isEqualTo(userId);
        assertThat(response.getEntityType()).isEqualTo(2);
        assertThat(response.getEntityId()).isEqualTo(entityId);
        assertThat(response.getPostId()).isEqualTo(postId);
        assertThat(response.getPostTitle()).isEqualTo("<title>");
        assertThat(response.getContent()).isEqualTo("<reply>");
    }
}
