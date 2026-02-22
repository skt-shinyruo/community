package com.nowcoder.community.content.domain.event;

import com.nowcoder.community.content.service.PostCommandService;
import com.nowcoder.community.infra.outbox.OutboxEventService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;

import java.sql.Timestamp;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest(properties = {
        "content.events.publisher=kafka",
        "events.outbox.enabled=true",
        "events.outbox.relay-enabled=false",
        "spring.task.scheduling.enabled=false"
})
class DomainEventOutboxAtomicityTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PostCommandService postCommandService;

    @MockBean
    private KafkaTemplate<String, String> kafkaTemplate;

    @SpyBean
    private OutboxEventService outboxEventService;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from outbox_event");
        jdbcTemplate.update("delete from discuss_post");

        jdbcTemplate.update(
                "insert into discuss_post(id, user_id, category_id, title, content, type, status, create_time, update_time, edit_count, deleted_by, deleted_reason, deleted_time, comment_count, score) " +
                        "values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                1, 10, 1, "t1", "c1", 0, 0,
                Timestamp.from(Instant.now()),
                Timestamp.from(Instant.now()),
                0, 0, null, null,
                0, 0.0
        );

        doAnswer(invocation -> {
            invocation.callRealMethod();
            throw new RuntimeException("enqueue failed");
        }).when(outboxEventService).enqueue(anyString(), anyString(), nullable(String.class), anyString());
    }

    @Test
    void shouldRollbackBusinessUpdateWhenOutboxEnqueueFails() {
        assertThrows(RuntimeException.class, () -> postCommandService.topPost(1, 1));
        Integer type = jdbcTemplate.queryForObject("select type from discuss_post where id = 1", Integer.class);
        assertEquals(0, type == null ? 0 : type);

        Integer outboxCount = jdbcTemplate.queryForObject("select count(1) from outbox_event", Integer.class);
        assertEquals(0, outboxCount == null ? 0 : outboxCount);
    }
}
