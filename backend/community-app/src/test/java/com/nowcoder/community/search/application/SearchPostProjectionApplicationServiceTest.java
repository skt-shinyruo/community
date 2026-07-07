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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SearchPostProjectionApplicationServiceTest {

    @Test
    void projectPostFromOutboxShouldRejectNullCommand() {
        PostScanQueryApi postScanQueryApi = mock(PostScanQueryApi.class);
        SearchApplicationService searchApplicationService = mock(SearchApplicationService.class);
        SearchPostProjectionApplicationService service =
                new SearchPostProjectionApplicationService(postScanQueryApi, searchApplicationService);

        assertThatThrownBy(() -> service.projectPostFromOutbox(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("command must not be null");
    }

    @Test
    void projectPostFromOutboxShouldIgnoreNullPostId() {
        PostScanQueryApi postScanQueryApi = mock(PostScanQueryApi.class);
        SearchApplicationService searchApplicationService = mock(SearchApplicationService.class);
        SearchPostProjectionApplicationService service =
                new SearchPostProjectionApplicationService(postScanQueryApi, searchApplicationService);

        service.projectPostFromOutbox(new ProjectPostOutboxCommand(null, "src", 1L));

        verifyNoInteractions(postScanQueryApi, searchApplicationService);
    }

    @Test
    void projectPostFromOutboxShouldRejectBlankSourceEventId() {
        PostScanQueryApi postScanQueryApi = mock(PostScanQueryApi.class);
        SearchApplicationService searchApplicationService = mock(SearchApplicationService.class);
        SearchPostProjectionApplicationService service =
                new SearchPostProjectionApplicationService(postScanQueryApi, searchApplicationService);

        assertThatThrownBy(() -> service.projectPostFromOutbox(new ProjectPostOutboxCommand(uuid(101), " ", 1L)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("source event id");
        verifyNoInteractions(postScanQueryApi, searchApplicationService);
    }

    @Test
    void projectPostFromOutboxShouldSkipWhenProjectionDisabled() {
        PostScanQueryApi postScanQueryApi = mock(PostScanQueryApi.class);
        SearchApplicationService searchApplicationService = mock(SearchApplicationService.class);
        SearchPolicyProperties policyProperties = new SearchPolicyProperties();
        policyProperties.setProjectionEnabled(false);
        SearchPostProjectionApplicationService service =
                new SearchPostProjectionApplicationService(postScanQueryApi, searchApplicationService, policyProperties);

        service.projectPostFromOutbox(new ProjectPostOutboxCommand(uuid(101), "src-disabled", 1L));

        verifyNoInteractions(postScanQueryApi, searchApplicationService);
    }

    @Test
    void projectPostFromOutboxShouldDeleteWhenProjectionNoLongerExists() {
        PostScanQueryApi postScanQueryApi = mock(PostScanQueryApi.class);
        SearchApplicationService searchApplicationService = mock(SearchApplicationService.class);
        SearchPostProjectionApplicationService service =
                new SearchPostProjectionApplicationService(postScanQueryApi, searchApplicationService);

        when(postScanQueryApi.getPostProjectionAllowDeleted(uuid(101))).thenReturn(null);

        service.projectPostFromOutbox(new ProjectPostOutboxCommand(uuid(101), "src-s3", 3L));

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

        service.projectPostFromOutbox(new ProjectPostOutboxCommand(uuid(101), "src-s1", 1L));

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

    @Test
    void repeatedPostUpdatesShouldSyncLatestContentProjection() {
        PostScanQueryApi postScanQueryApi = mock(PostScanQueryApi.class);
        SearchApplicationService searchApplicationService = mock(SearchApplicationService.class);
        SearchPostProjectionApplicationService service =
                new SearchPostProjectionApplicationService(postScanQueryApi, searchApplicationService);

        when(postScanQueryApi.getPostProjectionAllowDeleted(uuid(201)))
                .thenReturn(postProjection(uuid(201), "old title", "old content", 1.0))
                .thenReturn(postProjection(uuid(201), "latest title", "latest content", 2.0));

        service.projectPostFromOutbox(new ProjectPostOutboxCommand(uuid(201), "evt-update-1", 1L));
        service.projectPostFromOutbox(new ProjectPostOutboxCommand(uuid(201), "evt-update-2", 2L));

        ArgumentCaptor<SyncPostProjectionCommand> captor = ArgumentCaptor.forClass(SyncPostProjectionCommand.class);
        verify(searchApplicationService, times(2)).syncPostProjection(captor.capture());
        verify(searchApplicationService, never()).deletePost(org.mockito.ArgumentMatchers.any());
        assertThat(captor.getAllValues())
                .extracting(SyncPostProjectionCommand::title)
                .containsExactly("old title", "latest title");
        assertThat(captor.getAllValues().get(1).content()).isEqualTo("latest content");
        assertThat(captor.getAllValues().get(1).score()).isEqualTo(2.0);
    }

    @Test
    void repeatedPostDeletesShouldLeaveIndexDeletedWhenProjectionIsMissing() {
        PostScanQueryApi postScanQueryApi = mock(PostScanQueryApi.class);
        SearchApplicationService searchApplicationService = mock(SearchApplicationService.class);
        SearchPostProjectionApplicationService service =
                new SearchPostProjectionApplicationService(postScanQueryApi, searchApplicationService);

        when(postScanQueryApi.getPostProjectionAllowDeleted(uuid(202))).thenReturn(null);

        service.projectPostFromOutbox(new ProjectPostOutboxCommand(uuid(202), "evt-delete-1", 1L));
        service.projectPostFromOutbox(new ProjectPostOutboxCommand(uuid(202), "evt-delete-2", 2L));

        ArgumentCaptor<DeleteIndexedPostCommand> captor = ArgumentCaptor.forClass(DeleteIndexedPostCommand.class);
        verify(searchApplicationService, times(2)).deletePost(captor.capture());
        verify(searchApplicationService, never()).syncPostProjection(org.mockito.ArgumentMatchers.any());
        assertThat(captor.getAllValues())
                .extracting(DeleteIndexedPostCommand::postId)
                .containsExactly(uuid(202), uuid(202));
    }

    @Test
    void updateThenDeleteShouldEndDeletedWhenContentProjectionDisappears() {
        PostScanQueryApi postScanQueryApi = mock(PostScanQueryApi.class);
        SearchApplicationService searchApplicationService = mock(SearchApplicationService.class);
        SearchPostProjectionApplicationService service =
                new SearchPostProjectionApplicationService(postScanQueryApi, searchApplicationService);

        when(postScanQueryApi.getPostProjectionAllowDeleted(uuid(203)))
                .thenReturn(postProjection(uuid(203), "visible title", "visible content", 1.0))
                .thenReturn(null)
                .thenReturn(null);

        service.projectPostFromOutbox(new ProjectPostOutboxCommand(uuid(203), "evt-update", 1L));
        service.projectPostFromOutbox(new ProjectPostOutboxCommand(uuid(203), "evt-delete-1", 2L));
        service.projectPostFromOutbox(new ProjectPostOutboxCommand(uuid(203), "evt-delete-2", 3L));

        ArgumentCaptor<SyncPostProjectionCommand> syncCaptor = ArgumentCaptor.forClass(SyncPostProjectionCommand.class);
        ArgumentCaptor<DeleteIndexedPostCommand> deleteCaptor = ArgumentCaptor.forClass(DeleteIndexedPostCommand.class);
        verify(searchApplicationService).syncPostProjection(syncCaptor.capture());
        verify(searchApplicationService, times(2)).deletePost(deleteCaptor.capture());
        assertThat(syncCaptor.getValue().postId()).isEqualTo(uuid(203));
        assertThat(deleteCaptor.getAllValues())
                .extracting(DeleteIndexedPostCommand::postId)
                .containsExactly(uuid(203), uuid(203));
    }

    @Test
    void deleteThenUpdateShouldEndSyncedWhenContentProjectionExistsAgain() {
        PostScanQueryApi postScanQueryApi = mock(PostScanQueryApi.class);
        SearchApplicationService searchApplicationService = mock(SearchApplicationService.class);
        SearchPostProjectionApplicationService service =
                new SearchPostProjectionApplicationService(postScanQueryApi, searchApplicationService);

        when(postScanQueryApi.getPostProjectionAllowDeleted(uuid(204)))
                .thenReturn(null)
                .thenReturn(postProjection(uuid(204), "restored title", "restored content", 3.0))
                .thenReturn(postProjection(uuid(204), "restored title", "restored content", 3.0));

        service.projectPostFromOutbox(new ProjectPostOutboxCommand(uuid(204), "evt-delete", 1L));
        service.projectPostFromOutbox(new ProjectPostOutboxCommand(uuid(204), "evt-update-1", 2L));
        service.projectPostFromOutbox(new ProjectPostOutboxCommand(uuid(204), "evt-update-2", 3L));

        ArgumentCaptor<SyncPostProjectionCommand> syncCaptor = ArgumentCaptor.forClass(SyncPostProjectionCommand.class);
        verify(searchApplicationService).deletePost(new DeleteIndexedPostCommand(uuid(204)));
        verify(searchApplicationService, times(2)).syncPostProjection(syncCaptor.capture());
        assertThat(syncCaptor.getAllValues())
                .extracting(SyncPostProjectionCommand::title)
                .containsExactly("restored title", "restored title");
        assertThat(syncCaptor.getAllValues().get(1).postId()).isEqualTo(uuid(204));
    }

    @Test
    void replayedOldEventShouldNotReviveDeletedPost() throws Exception {
        PostScanQueryApi postScanQueryApi = mock(PostScanQueryApi.class);
        SearchApplicationService searchApplicationService = mock(SearchApplicationService.class);
        SearchPostProjectionApplicationService service =
                new SearchPostProjectionApplicationService(postScanQueryApi, searchApplicationService);
        java.util.UUID postId = uuid(205);

        when(postScanQueryApi.getPostProjectionAllowDeleted(postId)).thenReturn(null);

        service.projectPostFromOutbox(new ProjectPostOutboxCommand(postId, "evt-old-create-replayed", 1L));

        verify(searchApplicationService).deletePost(new DeleteIndexedPostCommand(postId));
        verify(searchApplicationService, never()).syncPostProjection(org.mockito.ArgumentMatchers.any());
    }

    private static PostScanView.PostProjectionView postProjection(
            java.util.UUID postId,
            String title,
            String content,
            double score
    ) {
        return new PostScanView.PostProjectionView(
                postId,
                uuid(7),
                uuid(3),
                List.of("java"),
                title,
                content,
                0,
                0,
                Instant.parse("2026-03-28T00:00:00Z"),
                score
        );
    }
}
