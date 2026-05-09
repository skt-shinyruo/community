package com.nowcoder.community.content.infrastructure.api;

import com.nowcoder.community.content.api.model.PostContentBlockPayload;
import com.nowcoder.community.content.api.model.PostCreateResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PostPublishingActionApiAdapterTest {

    private com.nowcoder.community.content.application.PostPublishingApplicationService applicationService;
    private PostPublishingActionApiAdapter adapter;

    @BeforeEach
    void setUp() {
        applicationService = mock(com.nowcoder.community.content.application.PostPublishingApplicationService.class);
        adapter = new PostPublishingActionApiAdapter(applicationService);
    }

    @Test
    void createShouldDelegateToOwnerApplicationServiceAndMapApiResult() {
        UUID userId = uuid(7);
        UUID categoryId = uuid(1);
        UUID postId = uuid(99);
        List<PostContentBlockPayload> blocks = List.of(new PostContentBlockPayload("paragraph", "<content>", null, "", "", "", null));

        when(applicationService.create(eq("idem-1"), argThat(command ->
                userId.equals(command.userId())
                        && "<title>".equals(command.title())
                        && categoryId.equals(command.categoryId())
                        && command.tags().equals(List.of("java"))
                        && command.blocks().size() == 1
                        && "<content>".equals(command.blocks().get(0).text())
        )))
                .thenReturn(new com.nowcoder.community.content.application.result.PostCreateResult(postId));

        PostCreateResult response = adapter.create(userId, "idem-1", "<title>", categoryId, List.of("java"), blocks);

        assertThat(response.postId()).isEqualTo(postId);
        verify(applicationService).create(eq("idem-1"), argThat(command ->
                userId.equals(command.userId())
                        && "<title>".equals(command.title())
                        && categoryId.equals(command.categoryId())
                        && command.tags().equals(List.of("java"))
                        && command.blocks().size() == 1
                        && "<content>".equals(command.blocks().get(0).text())
        ));
    }

    @Test
    void updateAndDeleteByAuthorShouldDelegateToOwnerApplicationService() {
        UUID userId = uuid(7);
        UUID postId = uuid(101);
        UUID categoryId = uuid(2);
        List<PostContentBlockPayload> blocks = List.of(new PostContentBlockPayload("paragraph", "<content>", null, "", "", "", null));

        adapter.updatePost(userId, postId, "<title>", categoryId, List.of("spring"), blocks);
        adapter.deleteByAuthor(userId, postId);

        verify(applicationService).updatePost(
                eq(userId),
                eq(postId),
                eq("<title>"),
                eq(categoryId),
                eq(List.of("spring")),
                argThat(commands -> commands.size() == 1 && "<content>".equals(commands.get(0).text()))
        );
        verify(applicationService).deleteByAuthor(userId, postId);
    }
}
