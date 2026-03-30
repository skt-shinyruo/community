package com.nowcoder.community.ops.controller;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.ops.dto.SearchReindexResponse;
import com.nowcoder.community.search.api.action.SearchReindexActionApi;
import com.nowcoder.community.search.api.model.SearchReindexResult;
import com.nowcoder.community.search.exception.SearchErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpsControllerTest {

    @Test
    void reindexShouldWrapServiceResponseAsOkResult() {
        SearchReindexActionApi searchReindexActionApi = mock(SearchReindexActionApi.class);
        when(searchReindexActionApi.reindex()).thenReturn(new SearchReindexResult("job-1", 42, false, null));

        OpsController controller = new OpsController(searchReindexActionApi);

        ResponseEntity<Result<SearchReindexResponse>> result = controller.reindex();

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getCode()).isEqualTo(0);
        assertThat(result.getBody().getData()).isNotNull();
        assertThat(result.getBody().getData().getJobId()).isEqualTo("job-1");
        assertThat(result.getBody().getData().getIndexedCount()).isEqualTo(42);
    }

    @Test
    void reindexShouldReturnBusinessErrorWithoutInternalCallMetrics() {
        SearchReindexActionApi searchReindexActionApi = mock(SearchReindexActionApi.class);
        when(searchReindexActionApi.reindex())
                .thenReturn(new SearchReindexResult("job-1", 0, true, "reindex 任务正在执行 (jobId=job-1)"));

        OpsController controller = new OpsController(searchReindexActionApi);

        ResponseEntity<Result<SearchReindexResponse>> result = controller.reindex();

        assertThat(result.getStatusCode().value()).isEqualTo(409);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getCode()).isEqualTo(SearchErrorCode.REINDEX_RUNNING.getCode());
        assertThat(result.getBody().getMessage()).isEqualTo("reindex 任务正在执行 (jobId=job-1)");
        assertThat(result.getBody().getData()).isNull();
    }

    @Test
    void reindexShouldPropagateUnexpectedRuntimeFailures() {
        SearchReindexActionApi searchReindexActionApi = mock(SearchReindexActionApi.class);
        OpsController controller = new OpsController(searchReindexActionApi);
        RuntimeException error = new RuntimeException("boom");
        when(searchReindexActionApi.reindex()).thenThrow(error);

        assertThatThrownBy(controller::reindex)
                .isSameAs(error);
    }
}
