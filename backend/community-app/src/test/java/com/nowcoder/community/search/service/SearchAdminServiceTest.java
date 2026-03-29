package com.nowcoder.community.search.service;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.search.api.action.SearchReindexActionApi;
import com.nowcoder.community.search.api.model.SearchReindexResult;
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
        SearchReindexActionApi searchReindexActionApi = mock(SearchReindexActionApi.class);
        ReindexJobService reindexJobService = spy(new ReindexJobService());
        when(searchReindexActionApi.reindex())
                .thenReturn(new SearchReindexResult("job-1", 0, true, "reindex 任务正在执行 (jobId=job-1)"));

        SearchAdminService service = new SearchAdminService(searchReindexActionApi, reindexJobService);

        assertThatThrownBy(service::reindex)
                .isInstanceOf(BusinessException.class)
                .hasMessage("reindex 任务正在执行 (jobId=job-1)")
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(SearchErrorCode.REINDEX_RUNNING);

        verify(searchReindexActionApi).reindex();
        verify(reindexJobService).conflict("job-1");
        verifyNoMoreInteractions(searchReindexActionApi, reindexJobService);
    }

    @Test
    void reindexShouldReturnResponseFromSuccessfulExecution() {
        SearchReindexActionApi searchReindexActionApi = mock(SearchReindexActionApi.class);
        ReindexJobService reindexJobService = mock(ReindexJobService.class);
        when(searchReindexActionApi.reindex())
                .thenReturn(new SearchReindexResult("job-2", 42, false, null));

        SearchAdminService service = new SearchAdminService(searchReindexActionApi, reindexJobService);

        SearchReindexResponse response = service.reindex();

        assertThat(response.getJobId()).isEqualTo("job-2");
        assertThat(response.getIndexedCount()).isEqualTo(42);
        verify(searchReindexActionApi).reindex();
        verifyNoInteractions(reindexJobService);
        verifyNoMoreInteractions(searchReindexActionApi);
    }
}
