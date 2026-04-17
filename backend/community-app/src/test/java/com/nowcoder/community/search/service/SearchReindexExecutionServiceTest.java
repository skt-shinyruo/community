package com.nowcoder.community.search.service;

import com.nowcoder.community.search.api.action.SearchReindexActionApi;
import com.nowcoder.community.search.api.model.SearchReindexResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class SearchReindexExecutionServiceTest {

    @Test
    void executeShouldRunReindexAndReleaseJobWhenAcquired(CapturedOutput output) {
        PostSearchService postSearchService = mock(PostSearchService.class);
        ReindexJobService reindexJobService = mock(ReindexJobService.class);
        ReindexJobService.ReindexJob job = new ReindexJobService.ReindexJob("job-1", true, null);
        when(reindexJobService.tryStart()).thenReturn(job);
        when(postSearchService.clearAndReindexFromContentService()).thenReturn(42);

        SearchReindexActionApi service =
                new SearchReindexExecutionService(postSearchService, reindexJobService);

        SearchReindexResult result = service.reindex();

        assertThat(result.jobId()).isEqualTo("job-1");
        assertThat(result.indexedCount()).isEqualTo(42);
        assertThat(result.skipped()).isFalse();
        assertThat(result.reason()).isNull();
        verify(reindexJobService).tryStart();
        verify(postSearchService).clearAndReindexFromContentService();
        verify(reindexJobService).finish(job);
        verifyNoMoreInteractions(postSearchService, reindexJobService);
        assertThat(output.getAll())
                .contains("community.category=async")
                .contains("community.action=search_reindex_start")
                .contains("community.outcome=success")
                .contains("community.job_id=job-1")
                .contains("community.action=search_reindex")
                .contains("community.outcome=success")
                .contains("community.indexed_count=42");
    }

    @Test
    void executeShouldReturnSkippedResultWhenJobAlreadyRunning(CapturedOutput output) {
        PostSearchService postSearchService = mock(PostSearchService.class);
        ReindexJobService reindexJobService = mock(ReindexJobService.class);
        when(reindexJobService.tryStart()).thenReturn(new ReindexJobService.ReindexJob("job-1", false, null));

        SearchReindexActionApi service =
                new SearchReindexExecutionService(postSearchService, reindexJobService);

        SearchReindexResult result = service.reindex();

        assertThat(result.jobId()).isEqualTo("job-1");
        assertThat(result.indexedCount()).isZero();
        assertThat(result.skipped()).isTrue();
        assertThat(result.reason()).contains("job-1");
        verify(reindexJobService).tryStart();
        verifyNoInteractions(postSearchService);
        verifyNoMoreInteractions(reindexJobService);
        assertThat(output.getAll())
                .contains("community.category=async")
                .contains("community.action=search_reindex")
                .contains("community.outcome=skipped")
                .contains("community.reason_code=already_running")
                .contains("community.job_id=job-1");
    }

    @Test
    void executeShouldReleaseJobWhenReindexFails(CapturedOutput output) {
        PostSearchService postSearchService = mock(PostSearchService.class);
        ReindexJobService reindexJobService = mock(ReindexJobService.class);
        RuntimeException boom = new RuntimeException("boom");
        ReindexJobService.ReindexJob job = new ReindexJobService.ReindexJob("job-1", true, null);
        when(reindexJobService.tryStart()).thenReturn(job);
        when(postSearchService.clearAndReindexFromContentService()).thenThrow(boom);

        SearchReindexActionApi service =
                new SearchReindexExecutionService(postSearchService, reindexJobService);

        assertThatThrownBy(service::reindex)
                .isSameAs(boom);

        verify(reindexJobService).tryStart();
        verify(postSearchService).clearAndReindexFromContentService();
        verify(reindexJobService).finish(job);
        verifyNoMoreInteractions(postSearchService, reindexJobService);
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
}
