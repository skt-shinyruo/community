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

@SpringBootTest
@AutoConfigureMockMvc
class PostControllerPathSemanticsTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from comment");
        jdbcTemplate.update("delete from discuss_post");

        jdbcTemplate.update(
                "insert into discuss_post(id, user_id, category_id, title, content, type, status, create_time, comment_count, score) values (?,?,?,?,?,?,?,?,?,?)",
                1, 1, 1, "p1", "c1", 0, 0, Timestamp.from(Instant.now()), 0, 0.0
        );
        jdbcTemplate.update(
                "insert into discuss_post(id, user_id, category_id, title, content, type, status, create_time, comment_count, score) values (?,?,?,?,?,?,?,?,?,?)",
                2, 1, 1, "p2", "c2", 0, 0, Timestamp.from(Instant.now()), 0, 0.0
        );

        // post#1 的直接评论（父评论）
        jdbcTemplate.update(
                "insert into comment(id, user_id, entity_type, entity_id, target_id, content, status, create_time) values (?,?,?,?,?,?,?,?)",
                10, 2, 1, 1, 0, "c10", 0, Timestamp.from(Instant.now())
        );
        // 回复 parent comment#10
        jdbcTemplate.update(
                "insert into comment(id, user_id, entity_type, entity_id, target_id, content, status, create_time) values (?,?,?,?,?,?,?,?)",
                11, 3, 2, 10, 0, "r11", 0, Timestamp.from(Instant.now())
        );
    }

    @Test
    void repliesShouldRejectCrossPostEnumeration() throws Exception {
        mockMvc.perform(get("/api/posts/2/comments/10/replies").param("page", "0").param("size", "10"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));

        mockMvc.perform(get("/api/posts/1/comments/10/replies").param("page", "0").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].id").value(11));
    }
}

