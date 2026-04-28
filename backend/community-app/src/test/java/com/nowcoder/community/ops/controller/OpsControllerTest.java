package com.nowcoder.community.ops.controller;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.ops.application.OpsApplicationService;
import com.nowcoder.community.ops.application.command.ReindexSearchCommand;
import com.nowcoder.community.ops.application.result.SearchReindexResult;
import com.nowcoder.community.ops.controller.dto.SearchReindexResponse;
import com.nowcoder.community.search.exception.SearchErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpsControllerTest {

    @Test
    void reindexShouldWrapServiceResponseAsOkResult() {
        OpsApplicationService opsApplicationService = mock(OpsApplicationService.class);
        when(opsApplicationService.reindexSearch(any(ReindexSearchCommand.class)))
                .thenReturn(new SearchReindexResult("job-1", 42, false, null));

        OpsController controller = new OpsController(opsApplicationService);

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
        OpsApplicationService opsApplicationService = mock(OpsApplicationService.class);
        when(opsApplicationService.reindexSearch(any(ReindexSearchCommand.class)))
                .thenReturn(new SearchReindexResult("job-1", 0, true, "reindex 任务正在执行 (jobId=job-1)"));

        OpsController controller = new OpsController(opsApplicationService);

        ResponseEntity<Result<SearchReindexResponse>> result = controller.reindex();

        assertThat(result.getStatusCode().value()).isEqualTo(409);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getCode()).isEqualTo(SearchErrorCode.REINDEX_RUNNING.getCode());
        assertThat(result.getBody().getMessage()).isEqualTo("reindex 任务正在执行 (jobId=job-1)");
        assertThat(result.getBody().getData()).isNull();
    }

    @Test
    void reindexShouldPropagateUnexpectedRuntimeFailures() {
        OpsApplicationService opsApplicationService = mock(OpsApplicationService.class);
        OpsController controller = new OpsController(opsApplicationService);
        RuntimeException error = new RuntimeException("boom");
        when(opsApplicationService.reindexSearch(any(ReindexSearchCommand.class))).thenThrow(error);

        assertThatThrownBy(controller::reindex)
                .isSameAs(error);
    }
}
