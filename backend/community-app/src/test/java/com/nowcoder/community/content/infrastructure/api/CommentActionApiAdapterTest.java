package com.nowcoder.community.content.infrastructure.api;

import com.nowcoder.community.content.api.action.CommentActionApi;
import com.nowcoder.community.content.application.CommentApplicationService;
import com.nowcoder.community.content.application.command.CreateCommentCommand;
import com.nowcoder.community.content.application.result.CommentCreateResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
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
        UUID parentCommentId = uuid(12);
        UUID commentId = uuid(21);
        CreateCommentCommand command = new CreateCommentCommand(userId, postId, parentCommentId, "<content>");

        when(applicationService.create("idem-1", command))
                .thenReturn(new CommentCreateResult(commentId));

        UUID result = adapter.addComment(userId, "idem-1", postId, parentCommentId, "<content>");

        assertThat(result).isEqualTo(commentId);
        verify(applicationService).create("idem-1", command);
    }

    @Test
    void updateCommentShouldDelegateToOwnerApplicationService() {
        UUID userId = uuid(7);
        UUID postId = uuid(11);
        UUID commentId = uuid(21);

        adapter.updateComment(userId, postId, commentId, "<content>");

        verify(applicationService).updateComment(userId, postId, commentId, "<content>");
    }

    @Test
    void addCommentContractShouldCarryOnlyDirectParentAndContent() {
        Class<?>[] parameterTypes = Arrays.stream(CommentActionApi.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("addComment"))
                .findFirst()
                .orElseThrow()
                .getParameterTypes();

        assertThat(parameterTypes).containsExactly(
                UUID.class,
                String.class,
                UUID.class,
                UUID.class,
                String.class
        );
    }
}
