package com.nowcoder.community.content.infrastructure.api;

import com.nowcoder.community.content.api.model.PostScanView;
import com.nowcoder.community.content.application.PostReadApplicationService;
import com.nowcoder.community.content.application.result.PostScanResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PostScanQueryApiAdapterTest {

    @Test
    void scanPostsShouldDelegateToApplicationService() {
        PostReadApplicationService applicationService = mock(PostReadApplicationService.class);
        UUID nextAfterId = uuid(10);
        PostScanResult result = new PostScanResult(List.of(), nextAfterId, false);
        when(applicationService.scanPosts(null, 5)).thenReturn(result);

        PostScanQueryApiAdapter service = new PostScanQueryApiAdapter(applicationService);

        PostScanView view = service.scanPosts(null, 5);

        assertThat(view.nextAfterId()).isEqualTo(nextAfterId);
        assertThat(view.hasMore()).isFalse();
        verify(applicationService).scanPosts(null, 5);
    }

    @Test
    void getPostProjectionAllowDeletedShouldDelegateToApplicationService() {
        PostReadApplicationService applicationService = mock(PostReadApplicationService.class);
        UUID postId = uuid(11);
        PostScanResult.PostProjectionResult result = new PostScanResult.PostProjectionResult(
                postId,
                uuid(3),
                uuid(4),
                List.of("search"),
                "title",
                "content",
                1,
                2,
                null,
                2.5
        );
        when(applicationService.getPostProjectionAllowDeleted(postId)).thenReturn(result);

        PostScanQueryApiAdapter service = new PostScanQueryApiAdapter(applicationService);

        PostScanView.PostProjectionView projection = service.getPostProjectionAllowDeleted(postId);

        assertThat(projection.postId()).isEqualTo(postId);
        assertThat(projection.tags()).containsExactly("search");
        assertThat(projection.status()).isEqualTo(2);
        verify(applicationService).getPostProjectionAllowDeleted(postId);
    }
}
