package com.nowcoder.community.search.service;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.search.dto.SearchReindexResponse;
import com.nowcoder.community.search.exception.SearchErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class SearchAdminServiceTest {

    @Test
    void reindexShouldInvokeConflictAndThrowWhenExecutionWasSkipped() {
        SearchReindexExecutionService executionService = mock(SearchReindexExecutionService.class);
        ReindexJobService reindexJobService = spy(new ReindexJobService());
        when(executionService.execute())
                .thenReturn(new SearchReindexExecutionService.ExecutionResult("job-1", 0, true, "reindex 任务正在执行 (jobId=job-1)"));

        SearchAdminService service = new SearchAdminService(executionService, reindexJobService);

        assertThatThrownBy(service::reindex)
                .isInstanceOf(BusinessException.class)
                .hasMessage("reindex 任务正在执行 (jobId=job-1)")
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(SearchErrorCode.REINDEX_RUNNING);

        verify(executionService).execute();
        verify(reindexJobService).conflict("job-1");
        verifyNoMoreInteractions(executionService, reindexJobService);
    }

    @Test
    void reindexShouldReturnResponseFromSuccessfulExecution() {
        SearchReindexExecutionService executionService = mock(SearchReindexExecutionService.class);
        ReindexJobService reindexJobService = mock(ReindexJobService.class);
        when(executionService.execute())
                .thenReturn(new SearchReindexExecutionService.ExecutionResult("job-2", 42, false, null));

        SearchAdminService service = new SearchAdminService(executionService, reindexJobService);

        SearchReindexResponse response = service.reindex();

        assertThat(response.getJobId()).isEqualTo("job-2");
        assertThat(response.getIndexedCount()).isEqualTo(42);
        verify(executionService).execute();
        verifyNoInteractions(reindexJobService);
        verifyNoMoreInteractions(executionService);
    }
}
