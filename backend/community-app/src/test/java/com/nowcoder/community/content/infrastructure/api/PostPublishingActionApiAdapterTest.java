package com.nowcoder.community.content.infrastructure.api;

import com.nowcoder.community.content.api.model.PostCreateResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
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

        when(applicationService.create(userId, "idem-1", "<title>", "<content>", categoryId, List.of("java")))
                .thenReturn(new com.nowcoder.community.content.application.result.PostCreateResult(postId));

        PostCreateResult response = adapter.create(userId, "idem-1", "<title>", "<content>", categoryId, List.of("java"));

        assertThat(response.postId()).isEqualTo(postId);
        verify(applicationService).create(userId, "idem-1", "<title>", "<content>", categoryId, List.of("java"));
    }

    @Test
    void updateAndDeleteByAuthorShouldDelegateToOwnerApplicationService() {
        UUID userId = uuid(7);
        UUID postId = uuid(101);
        UUID categoryId = uuid(2);

        adapter.updatePost(userId, postId, "<title>", "<content>", categoryId, List.of("spring"));
        adapter.deleteByAuthor(userId, postId);

        verify(applicationService).updatePost(userId, postId, "<title>", "<content>", categoryId, List.of("spring"));
        verify(applicationService).deleteByAuthor(userId, postId);
    }
}
