package com.nowcoder.community.search.application;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.search.application.command.ReindexPostsCommand;
import com.nowcoder.community.search.application.result.SearchReindexResult;
import com.nowcoder.community.search.exception.SearchErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class SearchAdminApplicationServiceTest {

    @Test
    void reindexShouldInvokeConflictWithoutJobIdWhenExecutionWasSkipped() {
        SearchReindexApplicationService searchReindexApplicationService = mock(SearchReindexApplicationService.class);
        ReindexJobApplicationService reindexJobApplicationService = mock(ReindexJobApplicationService.class);
        when(searchReindexApplicationService.reindex(new ReindexPostsCommand()))
                .thenReturn(new SearchReindexResult(null, 0, true, "reindex 任务正在执行"));
        doThrow(new BusinessException(SearchErrorCode.REINDEX_RUNNING, "reindex 任务正在执行"))
                .when(reindexJobApplicationService).conflict(null);

        SearchAdminApplicationService service = new SearchAdminApplicationService(
                searchReindexApplicationService,
                reindexJobApplicationService
        );

        assertThatThrownBy(service::reindex)
                .isInstanceOf(BusinessException.class)
                .hasMessage("reindex 任务正在执行")
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(SearchErrorCode.REINDEX_RUNNING);

        verify(searchReindexApplicationService).reindex(new ReindexPostsCommand());
        verify(reindexJobApplicationService).conflict(null);
        verifyNoMoreInteractions(searchReindexApplicationService, reindexJobApplicationService);
    }

    @Test
    void reindexShouldReturnResponseFromSuccessfulExecution() {
        SearchReindexApplicationService searchReindexApplicationService = mock(SearchReindexApplicationService.class);
        ReindexJobApplicationService reindexJobApplicationService = mock(ReindexJobApplicationService.class);
        when(searchReindexApplicationService.reindex(new ReindexPostsCommand()))
                .thenReturn(new SearchReindexResult("job-2", 42, false, null));

        SearchAdminApplicationService service = new SearchAdminApplicationService(
                searchReindexApplicationService,
                reindexJobApplicationService
        );

        SearchReindexResult response = service.reindex();

        assertThat(response.jobId()).isEqualTo("job-2");
        assertThat(response.indexedCount()).isEqualTo(42);
        verify(searchReindexApplicationService).reindex(new ReindexPostsCommand());
        verifyNoInteractions(reindexJobApplicationService);
        verifyNoMoreInteractions(searchReindexApplicationService);
    }
}
