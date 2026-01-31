package com.nowcoder.community.search.api;

import com.nowcoder.community.search.service.PostSearchService;
import com.nowcoder.community.search.service.ReindexJobService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = InternalSearchController.class)
@AutoConfigureMockMvc(addFilters = false)
class SearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PostSearchService postSearchService;

    @MockBean
    private ReindexJobService reindexJobService;

    @Test
    void reindexShouldAcceptOpsExternalPathHeader() throws Exception {
        given(reindexJobService.tryStart()).willReturn(new ReindexJobService.ReindexJob("job-1", true));
        given(postSearchService.clearAndReindexFromContentService()).willReturn(12);

        mockMvc.perform(post("/internal/search/reindex")
                        .header("X-External-Path", "/api/ops/search/reindex"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.jobId").value("job-1"))
                .andExpect(jsonPath("$.data.indexedCount").value(12));

        verify(reindexJobService).finish("job-1");
    }

    @Test
    void reindexShouldAcceptLegacyExternalPathHeaderDuringCompatibilityWindow() throws Exception {
        given(reindexJobService.tryStart()).willReturn(new ReindexJobService.ReindexJob("job-2", true));
        given(postSearchService.clearAndReindexFromContentService()).willReturn(7);

        mockMvc.perform(post("/internal/search/reindex")
                        .header("X-External-Path", "/api/search/internal/reindex"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.jobId").value("job-2"))
                .andExpect(jsonPath("$.data.indexedCount").value(7));

        verify(reindexJobService).finish("job-2");
    }
}

