package com.nowcoder.community.content.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ErrorContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from comment");
        jdbcTemplate.update("delete from discuss_post");
    }

    @Test
    void subscribedListShouldRequireAuth() throws Exception {
        mockMvc.perform(get("/api/posts").param("subscribed", "true"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.traceId").exists());
    }

    @Test
    void subscribedListShouldRejectNonNumericJwtSubject() throws Exception {
        mockMvc.perform(get("/api/posts")
                        .param("subscribed", "true")
                        .with(jwt().jwt(jwt -> jwt.subject("abc"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.traceId").exists());
    }

    @Test
    void postDetailShouldReturnNotFoundWith404Code() throws Exception {
        mockMvc.perform(get("/api/posts/1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(12001))
                .andExpect(jsonPath("$.traceId").exists());
    }
}
