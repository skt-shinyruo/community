package com.nowcoder.community.search.application;

import com.nowcoder.community.content.api.model.PostScanView;
import com.nowcoder.community.content.api.query.PostScanQueryApi;
import com.nowcoder.community.search.application.command.DeleteIndexedPostCommand;
import com.nowcoder.community.search.application.command.ProjectPostOutboxCommand;
import com.nowcoder.community.search.application.command.SyncPostProjectionCommand;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SearchPostProjectionApplicationServiceTest {

    @Test
    void projectPostFromOutboxShouldIgnoreNullPostId() {
        PostScanQueryApi postScanQueryApi = mock(PostScanQueryApi.class);
        SearchApplicationService searchApplicationService = mock(SearchApplicationService.class);
        SearchPostProjectionApplicationService service =
                new SearchPostProjectionApplicationService(postScanQueryApi, searchApplicationService);

        service.projectPostFromOutbox(new ProjectPostOutboxCommand(null, "src", "PostUpdated"));

        verifyNoInteractions(postScanQueryApi, searchApplicationService);
    }

    @Test
    void projectPostFromOutboxShouldDeleteWhenProjectionNoLongerExists() {
        PostScanQueryApi postScanQueryApi = mock(PostScanQueryApi.class);
        SearchApplicationService searchApplicationService = mock(SearchApplicationService.class);
        SearchPostProjectionApplicationService service =
                new SearchPostProjectionApplicationService(postScanQueryApi, searchApplicationService);

        when(postScanQueryApi.getPostProjectionAllowDeleted(uuid(101))).thenReturn(null);

        service.projectPostFromOutbox(new ProjectPostOutboxCommand(uuid(101), "src-s3", "PostDeleted"));

        verify(searchApplicationService).deletePost(new DeleteIndexedPostCommand(uuid(101)));
        verify(searchApplicationService, never()).syncPostProjection(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void projectPostFromOutboxShouldSyncCurrentProjectionWhenPostExists() {
        PostScanQueryApi postScanQueryApi = mock(PostScanQueryApi.class);
        SearchApplicationService searchApplicationService = mock(SearchApplicationService.class);
        SearchPostProjectionApplicationService service =
                new SearchPostProjectionApplicationService(postScanQueryApi, searchApplicationService);

        PostScanView.PostProjectionView doc = new PostScanView.PostProjectionView(
                uuid(101),
                uuid(7),
                uuid(3),
                List.of("java"),
                "title",
                "content",
                0,
                0,
                Instant.parse("2026-03-28T00:00:00Z"),
                1.5
        );
        when(postScanQueryApi.getPostProjectionAllowDeleted(uuid(101))).thenReturn(doc);

        service.projectPostFromOutbox(new ProjectPostOutboxCommand(uuid(101), "src-s1", "PostUpdated"));

        ArgumentCaptor<SyncPostProjectionCommand> captor = ArgumentCaptor.forClass(SyncPostProjectionCommand.class);
        verify(searchApplicationService).syncPostProjection(captor.capture());
        verify(searchApplicationService, never()).deletePost(org.mockito.ArgumentMatchers.any());
        assertThat(captor.getValue().postId()).isEqualTo(uuid(101));
        assertThat(captor.getValue().userId()).isEqualTo(uuid(7));
        assertThat(captor.getValue().categoryId()).isEqualTo(uuid(3));
        assertThat(captor.getValue().tags()).containsExactly("java");
        assertThat(captor.getValue().title()).isEqualTo("title");
        assertThat(captor.getValue().content()).isEqualTo("content");
        assertThat(captor.getValue().status()).isEqualTo(0);
    }
}
