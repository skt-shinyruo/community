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
class PostControllerTest {

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
                1, 1, 1, "t1", "c1", 0, 0,
                Timestamp.from(Instant.now()),
                Timestamp.from(Instant.now()),
                0, 0, null, null,
                0, 0.0
        );

        jdbcTemplate.update(
                "insert into comment(id, user_id, entity_type, entity_id, target_id, content, status, create_time, update_time, edit_count, deleted_by, deleted_reason, deleted_time) " +
                        "values (?,?,?,?,?,?,?,?,?,?,?,?,?)",
                1, 2, 1, 1, 0, "hello",
                0,
                Timestamp.from(Instant.now()),
                Timestamp.from(Instant.now()),
                1,
                999,
                "internal reason",
                Timestamp.from(Instant.now())
        );
    }

    @Test
    void commentsShouldNotExposeGovernanceFields() throws Exception {
        mockMvc.perform(get("/api/posts/1/comments").param("page", "0").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].userId").value(2))
                .andExpect(jsonPath("$.data[0].content").value("hello"))
                .andExpect(jsonPath("$.data[0].createTime").exists())
                .andExpect(jsonPath("$.data[0].editCount").exists())
                .andExpect(jsonPath("$.data[0].updateTime").exists())
                .andExpect(jsonPath("$.data[0].status").doesNotExist())
                .andExpect(jsonPath("$.data[0].deletedBy").doesNotExist())
                .andExpect(jsonPath("$.data[0].deletedReason").doesNotExist())
                .andExpect(jsonPath("$.data[0].deletedTime").doesNotExist());
    }
}

