package com.nowcoder.community.search.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.common.web.SecurityExceptionHandler;
import com.nowcoder.community.search.config.SearchSecurityConfig;
import com.nowcoder.community.search.service.PostSearchService;
import com.nowcoder.community.search.service.ReindexJobService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.TestPropertySource;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = InternalSearchController.class)
@AutoConfigureMockMvc
@Import({SearchSecurityConfig.class, InternalSearchControllerSecurityContractTest.TestConfig.class})
@TestPropertySource(properties = {
        "security.jwt.hmac-secret=test-jwt-secret-please-change-at-least-32bytes"
})
class InternalSearchControllerSecurityContractTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PostSearchService postSearchService;

    @MockBean
    private ReindexJobService reindexJobService;

    @Test
    void internalReindexShouldAllowWithoutAuth() throws Exception {
        given(reindexJobService.tryStart()).willReturn(new ReindexJobService.ReindexJob("job-1", true));
        given(postSearchService.clearAndReindexFromContentService()).willReturn(1);

        mockMvc.perform(post("/internal/search/reindex"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.traceId").exists());
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        public SecurityExceptionHandler securityExceptionHandler(ObjectMapper objectMapper) {
            return new SecurityExceptionHandler(objectMapper);
        }
    }
}
