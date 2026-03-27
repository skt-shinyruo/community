package com.nowcoder.community.ops.controller;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.search.dto.SearchReindexResponse;
import com.nowcoder.community.search.exception.SearchErrorCode;
import com.nowcoder.community.search.service.SearchAdminService;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpsControllerTest {

    @Test
    void reindexShouldWrapServiceResponseAsOkResult() {
        SearchAdminService searchAdminService = mock(SearchAdminService.class);
        SearchReindexResponse response = new SearchReindexResponse();
        response.setJobId("job-1");
        response.setIndexedCount(42);
        when(searchAdminService.reindex()).thenReturn(response);

        OpsController controller = new OpsController(searchAdminService);

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
        SearchAdminService searchAdminService = mock(SearchAdminService.class);
        when(searchAdminService.reindex())
                .thenThrow(new BusinessException(SearchErrorCode.REINDEX_RUNNING, "reindex 任务正在执行 (jobId=job-1)"));

        OpsController controller = new OpsController(searchAdminService);

        ResponseEntity<Result<SearchReindexResponse>> result = controller.reindex();

        assertThat(result.getStatusCode().value()).isEqualTo(409);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getCode()).isEqualTo(SearchErrorCode.REINDEX_RUNNING.getCode());
        assertThat(result.getBody().getMessage()).isEqualTo("reindex 任务正在执行 (jobId=job-1)");
        assertThat(result.getBody().getData()).isNull();
    }

    @Test
    void reindexShouldPropagateUnexpectedRuntimeFailures() {
        SearchAdminService searchAdminService = mock(SearchAdminService.class);
        OpsController controller = new OpsController(searchAdminService);
        RuntimeException error = new RuntimeException("boom");
        when(searchAdminService.reindex()).thenThrow(error);

        assertThatThrownBy(controller::reindex)
                .isSameAs(error);
    }
}
