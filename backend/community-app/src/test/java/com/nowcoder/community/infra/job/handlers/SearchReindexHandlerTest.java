package com.nowcoder.community.infra.job.handlers;

import com.nowcoder.community.search.service.SearchReindexExecutionService;
import com.xxl.job.core.context.XxlJobContext;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class SearchReindexHandlerTest {

    @AfterEach
    void tearDown() {
        XxlJobContext.setXxlJobContext(null);
    }

    @Test
    void reindexShouldReportSuccessWhenExecutionRuns() {
        SearchReindexExecutionService executionService = mock(SearchReindexExecutionService.class);
        when(executionService.execute())
                .thenReturn(new SearchReindexExecutionService.ExecutionResult("job-1", 42, false, null));

        SearchReindexHandler handler = new SearchReindexHandler(executionService);
        XxlJobContext context = new XxlJobContext(1L, "", 2L, System.currentTimeMillis(), "", 0, 1);
        XxlJobContext.setXxlJobContext(context);

        handler.reindex();

        verify(executionService, times(1)).execute();
        verifyNoMoreInteractions(executionService);
        assertThat(context.getHandleCode()).isEqualTo(XxlJobContext.HANDLE_CODE_SUCCESS);
        assertThat(context.getHandleMsg()).contains("job-1");
        assertThat(context.getHandleMsg()).contains("42");
    }

    @Test
    void reindexShouldTreatSkippedConflictAsSuccess() {
        SearchReindexExecutionService executionService = mock(SearchReindexExecutionService.class);
        when(executionService.execute()).thenReturn(
                new SearchReindexExecutionService.ExecutionResult("job-1", 0, true, "reindex 任务正在执行 (jobId=job-1)")
        );

        SearchReindexHandler handler = new SearchReindexHandler(executionService);
        XxlJobContext context = new XxlJobContext(1L, "", 2L, System.currentTimeMillis(), "", 0, 1);
        XxlJobContext.setXxlJobContext(context);

        handler.reindex();

        verify(executionService, times(1)).execute();
        verifyNoMoreInteractions(executionService);
        assertThat(context.getHandleCode()).isEqualTo(XxlJobContext.HANDLE_CODE_SUCCESS);
        assertThat(context.getHandleMsg()).contains("skipped");
        assertThat(context.getHandleMsg()).contains("job-1");
    }

    @Test
    void reindexShouldUseSearchReindexJobName() throws NoSuchMethodException {
        Method method = SearchReindexHandler.class.getDeclaredMethod("reindex");

        XxlJob annotation = method.getAnnotation(XxlJob.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo("searchReindex");
    }

    @Test
    void reindexShouldMarkFailureWithoutThrowingWhenExecutionFails() {
        SearchReindexExecutionService executionService = mock(SearchReindexExecutionService.class);
        when(executionService.execute()).thenThrow(new RuntimeException("boom"));

        SearchReindexHandler handler = new SearchReindexHandler(executionService);
        XxlJobContext context = new XxlJobContext(1L, "", 2L, System.currentTimeMillis(), "", 0, 1);
        XxlJobContext.setXxlJobContext(context);

        handler.reindex();

        verify(executionService, times(1)).execute();
        verifyNoMoreInteractions(executionService);
        assertThat(context.getHandleCode()).isEqualTo(XxlJobContext.HANDLE_CODE_FAIL);
        assertThat(context.getHandleMsg()).contains("boom");
    }
}
