package com.nowcoder.community.content.domain.assembler;

import com.nowcoder.community.content.api.event.payload.PostPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class PostPayloadAssemblerTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PostPayloadAssembler assembler;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from post_tag");
        jdbcTemplate.update("delete from tag");
        jdbcTemplate.update("delete from discuss_post");
        jdbcTemplate.update("delete from category");

        jdbcTemplate.update(
                "insert into category(id, name, description, position, create_time) values (?,?,?,?,?)",
                1, "c1", "d1", 1, Timestamp.from(Instant.now())
        );

        jdbcTemplate.update(
                "insert into discuss_post(id, user_id, category_id, title, content, type, status, create_time, update_time, edit_count, deleted_by, deleted_reason, deleted_time, comment_count, score) " +
                        "values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                1, 10, 1, "t1", "c1", 0, 0,
                Timestamp.from(Instant.now()),
                Timestamp.from(Instant.now()),
                0, 0, null, null,
                0, 12.5
        );

        jdbcTemplate.update("insert into tag(id, name, create_time) values (?,?,?)", 1, "java", Timestamp.from(Instant.now()));
        jdbcTemplate.update("insert into tag(id, name, create_time) values (?,?,?)", 2, "spring", Timestamp.from(Instant.now()));
        jdbcTemplate.update("insert into post_tag(post_id, tag_id, create_time) values (?,?,?)", 1, 1, Timestamp.from(Instant.now()));
        jdbcTemplate.update("insert into post_tag(post_id, tag_id, create_time) values (?,?,?)", 1, 2, Timestamp.from(Instant.now()));
    }

    @Test
    void shouldAssembleFullPayloadWithTags() {
        PostPayload payload = assembler.assemble(1);
        assertNotNull(payload);
        assertEquals(1, payload.getPostId());
        assertEquals(10, payload.getUserId());
        assertEquals(1, payload.getCategoryId());
        assertEquals("t1", payload.getTitle());
        assertEquals("c1", payload.getContent());
        assertEquals(0, payload.getType());
        assertEquals(0, payload.getStatus());
        assertEquals(12.5, payload.getScore());

        List<String> tags = payload.getTags();
        assertNotNull(tags);
        assertTrue(tags.contains("java"));
        assertTrue(tags.contains("spring"));
    }
}

