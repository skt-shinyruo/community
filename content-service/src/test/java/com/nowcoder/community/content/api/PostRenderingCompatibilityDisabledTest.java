package com.nowcoder.community.content.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.Timestamp;
import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "content.render.legacy-entity-unescape-enabled=false"
})
@AutoConfigureMockMvc
class PostRenderingCompatibilityDisabledTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from comment");
        jdbcTemplate.update("delete from discuss_post");

        jdbcTemplate.update(
                "insert into discuss_post(id, user_id, category_id, title, content, type, status, create_time, update_time, edit_count, deleted_by, deleted_reason, deleted_time, comment_count, score) " +
                        "values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                1, 1, 1,
                "&lt;hello&gt;",
                "&lt;script&gt;alert(1)&lt;/script&gt;",
                0, 0,
                Timestamp.from(Instant.now()),
                Timestamp.from(Instant.now()),
                0, 0, null, null,
                0, 0.0
        );
    }

    @Test
    void postDetailShouldNotUnescapeWhenDisabled() throws Exception {
        mockMvc.perform(get("/api/posts/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.title").value("&lt;hello&gt;"))
                .andExpect(jsonPath("$.data.content").value("&lt;script&gt;alert(1)&lt;/script&gt;"));
    }
}

