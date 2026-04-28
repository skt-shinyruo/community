package com.nowcoder.community.ops.application;

import com.nowcoder.community.ops.application.command.ReindexSearchCommand;
import com.nowcoder.community.search.api.action.SearchReindexActionApi;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsApplicationServiceTest {

    @Test
    void reindexSearchShouldDelegateToSearchOwnerApi() {
        SearchReindexActionApi searchApi = mock(SearchReindexActionApi.class);
        when(searchApi.reindex())
                .thenReturn(new com.nowcoder.community.search.api.model.SearchReindexResult("job-1", 12, false, null));
        OpsApplicationService service = new OpsApplicationService(searchApi);

        com.nowcoder.community.ops.application.result.SearchReindexResult result =
                service.reindexSearch(new ReindexSearchCommand());

        assertThat(result.jobId()).isEqualTo("job-1");
        assertThat(result.indexedCount()).isEqualTo(12);
        verify(searchApi).reindex();
    }
}
