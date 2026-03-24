package com.nowcoder.community.search.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class SearchReindexExecutionServiceTest {

    @Test
    void executeShouldRunReindexAndReleaseJobWhenAcquired() {
        PostSearchService postSearchService = mock(PostSearchService.class);
        ReindexJobService reindexJobService = mock(ReindexJobService.class);
        when(reindexJobService.tryStart()).thenReturn(new ReindexJobService.ReindexJob("job-1", true));
        when(postSearchService.clearAndReindexFromContentService()).thenReturn(42);

        SearchReindexExecutionService service =
                new SearchReindexExecutionService(postSearchService, reindexJobService);

        SearchReindexExecutionService.ExecutionResult result = service.execute();

        assertThat(result.jobId()).isEqualTo("job-1");
        assertThat(result.indexedCount()).isEqualTo(42);
        assertThat(result.skipped()).isFalse();
        assertThat(result.reason()).isNull();
        verify(reindexJobService).tryStart();
        verify(postSearchService).clearAndReindexFromContentService();
        verify(reindexJobService).finish("job-1");
        verifyNoMoreInteractions(postSearchService, reindexJobService);
    }

    @Test
    void executeShouldReturnSkippedResultWhenJobAlreadyRunning() {
        PostSearchService postSearchService = mock(PostSearchService.class);
        ReindexJobService reindexJobService = mock(ReindexJobService.class);
        when(reindexJobService.tryStart()).thenReturn(new ReindexJobService.ReindexJob("job-1", false));

        SearchReindexExecutionService service =
                new SearchReindexExecutionService(postSearchService, reindexJobService);

        SearchReindexExecutionService.ExecutionResult result = service.execute();

        assertThat(result.jobId()).isEqualTo("job-1");
        assertThat(result.indexedCount()).isZero();
        assertThat(result.skipped()).isTrue();
        assertThat(result.reason()).contains("job-1");
        verify(reindexJobService).tryStart();
        verifyNoInteractions(postSearchService);
        verifyNoMoreInteractions(reindexJobService);
    }

    @Test
    void executeShouldReleaseJobWhenReindexFails() {
        PostSearchService postSearchService = mock(PostSearchService.class);
        ReindexJobService reindexJobService = mock(ReindexJobService.class);
        RuntimeException boom = new RuntimeException("boom");
        when(reindexJobService.tryStart()).thenReturn(new ReindexJobService.ReindexJob("job-1", true));
        when(postSearchService.clearAndReindexFromContentService()).thenThrow(boom);

        SearchReindexExecutionService service =
                new SearchReindexExecutionService(postSearchService, reindexJobService);

        assertThatThrownBy(service::execute)
                .isSameAs(boom);

        verify(reindexJobService).tryStart();
        verify(postSearchService).clearAndReindexFromContentService();
        verify(reindexJobService).finish("job-1");
        verifyNoMoreInteractions(postSearchService, reindexJobService);
    }
}
