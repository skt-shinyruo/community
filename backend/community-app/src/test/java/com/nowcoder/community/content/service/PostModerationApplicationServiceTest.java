package com.nowcoder.community.content.service;

import com.nowcoder.community.content.app.post.AdminDeletePostUseCase;
import com.nowcoder.community.content.app.post.MarkPostWonderfulUseCase;
import com.nowcoder.community.content.app.post.TopPostUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(OutputCaptureExtension.class)
class PostModerationApplicationServiceTest {

    private TopPostUseCase topPostUseCase;
    private MarkPostWonderfulUseCase markPostWonderfulUseCase;
    private AdminDeletePostUseCase adminDeletePostUseCase;
    private PostModerationApplicationService service;

    @BeforeEach
    void setUp() {
        topPostUseCase = mock(TopPostUseCase.class);
        markPostWonderfulUseCase = mock(MarkPostWonderfulUseCase.class);
        adminDeletePostUseCase = mock(AdminDeletePostUseCase.class);
        service = new PostModerationApplicationService(
                topPostUseCase,
                markPostWonderfulUseCase,
                adminDeletePostUseCase,
                new PostBusinessEventLogger()
        );
    }

    @Test
    void topAndWonderfulShouldLogBusinessEvents(CapturedOutput output) {
        UUID userId = uuid(9);
        UUID postId = uuid(101);

        service.top(userId, postId);
        service.wonderful(userId, postId);

        verify(topPostUseCase).topPost(userId, postId);
        verify(markPostWonderfulUseCase).markWonderful(userId, postId);
        assertThat(output.getAll())
                .contains("community.action=post_top")
                .contains("community.action=post_wonderful")
                .contains("community.target_type=post")
                .contains("community.target_id=" + postId)
                .contains("user.id=" + userId);
    }

    @Test
    void deleteShouldLogAdminDeleteBusinessEvent(CapturedOutput output) {
        UUID userId = uuid(99);
        UUID postId = uuid(101);

        service.delete(userId, postId);

        verify(adminDeletePostUseCase).adminDelete(userId, postId);
        assertThat(output.getAll())
                .contains("community.action=post_delete")
                .contains("community.reason_code=admin_delete")
                .contains("community.target_type=post")
                .contains("community.target_id=" + postId)
                .contains("user.id=" + userId);
    }
}
