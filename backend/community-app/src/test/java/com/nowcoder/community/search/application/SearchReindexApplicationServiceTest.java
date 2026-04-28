package com.nowcoder.community.search.application;

import com.nowcoder.community.content.api.model.PostScanView;
import com.nowcoder.community.content.api.query.PostScanQueryApi;
import com.nowcoder.community.search.application.command.ReindexPostsCommand;
import com.nowcoder.community.search.application.result.SearchReindexResult;
import com.nowcoder.community.search.config.PostScanProperties;
import com.nowcoder.community.search.domain.repository.PostSearchIndexRepository;
import com.nowcoder.community.search.domain.repository.PostSearchRepository;
import com.nowcoder.community.search.domain.service.SearchReindexDomainService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class SearchReindexApplicationServiceTest {

    @Test
    void executeShouldRunReindexAndReleaseJobWhenAcquired(CapturedOutput output) {
        PostSearchRepository repository = mock(PostSearchRepository.class);
        PostScanQueryApi postScanQueryApi = mock(PostScanQueryApi.class);
        ReindexJobApplicationService reindexJobApplicationService = mock(ReindexJobApplicationService.class);
        ReindexJobApplicationService.RenewalHandle renewalHandle = mock(ReindexJobApplicationService.RenewalHandle.class);
        ReindexJobApplicationService.ReindexJob job = new ReindexJobApplicationService.ReindexJob("job-1", true, null);
        UUID postId = uuid(1001);
        when(reindexJobApplicationService.tryStart()).thenReturn(job);
        when(reindexJobApplicationService.startRenewal(job)).thenReturn(renewalHandle);
        when(postScanQueryApi.scanPosts(null, 10)).thenReturn(new PostScanView(
                List.of(projection(postId)),
                postId,
                false
        ));

        SearchReindexApplicationService service = newService(
                repository,
                postScanQueryApi,
                reindexJobApplicationService,
                emptyIndexProvider(),
                10
        );

        SearchReindexResult result = service.reindex(new ReindexPostsCommand());

        assertThat(result.jobId()).isEqualTo("job-1");
        assertThat(result.indexedCount()).isEqualTo(1);
        assertThat(result.skipped()).isFalse();
        assertThat(result.reason()).isNull();
        verify(reindexJobApplicationService).tryStart();
        verify(reindexJobApplicationService).startRenewal(job);
        verify(repository).clear();
        verify(repository).save(org.mockito.ArgumentMatchers.argThat(document -> postId.equals(document.postId())));
        verify(renewalHandle).close();
        verify(reindexJobApplicationService).finish(job);
        verifyNoMoreInteractions(repository, reindexJobApplicationService);
        assertThat(output.getAll())
                .contains("community.category=async")
                .contains("community.action=search_reindex_start")
                .contains("community.outcome=success")
                .contains("community.job_id=job-1")
                .contains("community.action=search_reindex")
                .contains("community.outcome=success")
                .contains("community.indexed_count=1");
    }

    @Test
    void executeShouldReturnSkippedResultWhenJobAlreadyRunning(CapturedOutput output) {
        PostSearchRepository repository = mock(PostSearchRepository.class);
        PostScanQueryApi postScanQueryApi = mock(PostScanQueryApi.class);
        ReindexJobApplicationService reindexJobApplicationService = mock(ReindexJobApplicationService.class);
        when(reindexJobApplicationService.tryStart())
                .thenReturn(new ReindexJobApplicationService.ReindexJob("job-1", false, null));

        SearchReindexApplicationService service = newService(
                repository,
                postScanQueryApi,
                reindexJobApplicationService,
                emptyIndexProvider(),
                10
        );

        SearchReindexResult result = service.reindex(new ReindexPostsCommand());

        assertThat(result.jobId()).isEqualTo("job-1");
        assertThat(result.indexedCount()).isZero();
        assertThat(result.skipped()).isTrue();
        assertThat(result.reason()).contains("job-1");
        verify(reindexJobApplicationService).tryStart();
        verifyNoInteractions(repository, postScanQueryApi);
        verifyNoMoreInteractions(reindexJobApplicationService);
        assertThat(output.getAll())
                .contains("community.category=async")
                .contains("community.action=search_reindex")
                .contains("community.outcome=skipped")
                .contains("community.reason_code=already_running")
                .contains("community.job_id=job-1");
    }

    @Test
    void executeShouldReleaseJobWhenReindexFails(CapturedOutput output) {
        PostSearchRepository repository = mock(PostSearchRepository.class);
        PostScanQueryApi postScanQueryApi = mock(PostScanQueryApi.class);
        ReindexJobApplicationService reindexJobApplicationService = mock(ReindexJobApplicationService.class);
        ReindexJobApplicationService.RenewalHandle renewalHandle = mock(ReindexJobApplicationService.RenewalHandle.class);
        RuntimeException boom = new RuntimeException("boom");
        ReindexJobApplicationService.ReindexJob job = new ReindexJobApplicationService.ReindexJob("job-1", true, null);
        when(reindexJobApplicationService.tryStart()).thenReturn(job);
        when(reindexJobApplicationService.startRenewal(job)).thenReturn(renewalHandle);
        when(postScanQueryApi.scanPosts(null, 10)).thenThrow(boom);

        SearchReindexApplicationService service = newService(
                repository,
                postScanQueryApi,
                reindexJobApplicationService,
                emptyIndexProvider(),
                10
        );

        assertThatThrownBy(() -> service.reindex(new ReindexPostsCommand()))
                .isSameAs(boom);

        verify(reindexJobApplicationService).tryStart();
        verify(reindexJobApplicationService).startRenewal(job);
        verify(repository).clear();
        verify(renewalHandle).close();
        verify(reindexJobApplicationService).finish(job);
        verifyNoMoreInteractions(repository, reindexJobApplicationService);
        assertThat(output.getAll())
                .contains("community.category=async")
                .contains("community.action=search_reindex_start")
                .contains("community.outcome=success")
                .contains("community.job_id=job-1")
                .contains("community.action=search_reindex")
                .contains("community.outcome=failure")
                .contains("community.reason_code=reindex_failed")
                .contains("community.job_id=job-1")
                .doesNotContain("community.action=search_reindex community.outcome=success");
    }

    private static SearchReindexApplicationService newService(
            PostSearchRepository repository,
            PostScanQueryApi postScanQueryApi,
            ReindexJobApplicationService reindexJobApplicationService,
            ObjectProvider<PostSearchIndexRepository> indexRepositoryProvider,
            int pageSize
    ) {
        PostScanProperties properties = new PostScanProperties();
        properties.setPageSize(pageSize);
        return new SearchReindexApplicationService(
                repository,
                postScanQueryApi,
                properties,
                indexRepositoryProvider,
                reindexJobApplicationService,
                new SearchReindexDomainService()
        );
    }

    private static ObjectProvider<PostSearchIndexRepository> emptyIndexProvider() {
        return new StaticListableBeanFactory().getBeanProvider(PostSearchIndexRepository.class);
    }

    private static PostScanView.PostProjectionView projection(UUID postId) {
        return new PostScanView.PostProjectionView(
                postId,
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
    }
}
