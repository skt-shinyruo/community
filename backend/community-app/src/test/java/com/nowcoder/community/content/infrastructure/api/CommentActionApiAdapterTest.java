package com.nowcoder.community.content.infrastructure.api;

import com.nowcoder.community.content.application.CommentApplicationService;
import com.nowcoder.community.content.application.result.CommentCreateResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommentActionApiAdapterTest {

    private CommentApplicationService applicationService;
    private CommentActionApiAdapter adapter;

    @BeforeEach
    void setUp() {
        applicationService = mock(CommentApplicationService.class);
        adapter = new CommentActionApiAdapter(applicationService);
    }

    @Test
    void addCommentShouldDelegateToOwnerApplicationServiceAndMapResult() {
        UUID userId = uuid(7);
        UUID postId = uuid(11);
        UUID entityId = uuid(12);
        UUID targetId = uuid(13);
        UUID commentId = uuid(21);

        when(applicationService.create(userId, "idem-1", postId, 1, entityId, targetId, "<content>"))
                .thenReturn(new CommentCreateResult(commentId));

        UUID result = adapter.addComment(userId, "idem-1", postId, 1, entityId, targetId, "<content>");

        assertThat(result).isEqualTo(commentId);
        verify(applicationService).create(userId, "idem-1", postId, 1, entityId, targetId, "<content>");
    }

    @Test
    void updateCommentShouldDelegateToOwnerApplicationService() {
        UUID userId = uuid(7);
        UUID postId = uuid(11);
        UUID commentId = uuid(21);

        adapter.updateComment(userId, postId, commentId, "<content>");

        verify(applicationService).updateComment(userId, postId, commentId, "<content>");
    }
}
