package com.nowcoder.community.search.api;

import com.nowcoder.community.search.service.PostSearchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SearchSecurityContractTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    PostSearchService postSearchService;

    @Test
    void searchPostsShouldBePublicButOtherPathsShouldRequireAuth() throws Exception {
        when(postSearchService.search(any(), any(), any(), any(), any())).thenReturn(List.of());

        mockMvc.perform(get("/api/search/posts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.traceId").exists());

        mockMvc.perform(get("/api/search/__test__"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.traceId").exists());

        // legacy ops endpoint：不再提供功能语义，固定返回 410 + successor link（不应被 401/403 遮蔽）
        mockMvc.perform(post("/api/search/internal/reindex"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value(410))
                .andExpect(jsonPath("$.traceId").exists())
                .andExpect(header().string("X-Successor", "/api/ops/search/reindex"))
                .andExpect(header().string(HttpHeaders.LINK, "<" + "/api/ops/search/reindex" + ">; rel=\"successor\""));
    }
}
