package com.nowcoder.community.user.outbox;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class OutboxEventServiceTest {

    @Autowired
    OutboxEventService outboxEventService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetTable() {
        jdbcTemplate.update("delete from outbox_event");
    }

    @Test
    void recoverStuckSendingShouldMoveToRetry() {
        Instant now = Instant.now();
        Timestamp createdAt = Timestamp.from(now.minus(Duration.ofHours(1)));
        Timestamp staleUpdatedAt = Timestamp.from(now.minus(Duration.ofMinutes(10)));
        Timestamp freshUpdatedAt = Timestamp.from(now.minus(Duration.ofSeconds(10)));

        jdbcTemplate.update(
                "insert into outbox_event(event_id, topic, event_key, payload, status, retry_count, next_retry_at, last_error, created_at, updated_at) values (?,?,?,?,?,?,?,?,?,?)",
                "e-stale",
                "t",
                "k",
                "{}",
                "SENDING",
                0,
                null,
                null,
                createdAt,
                staleUpdatedAt
        );
        jdbcTemplate.update(
                "insert into outbox_event(event_id, topic, event_key, payload, status, retry_count, next_retry_at, last_error, created_at, updated_at) values (?,?,?,?,?,?,?,?,?,?)",
                "e-fresh",
                "t",
                "k",
                "{}",
                "SENDING",
                0,
                null,
                null,
                createdAt,
                freshUpdatedAt
        );

        int recovered = outboxEventService.recoverStuckSending(Duration.ofMinutes(5).toMillis(), 100);

        assertThat(recovered).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select status from outbox_event where event_id = ?",
                String.class,
                "e-stale"
        )).isEqualTo("RETRY");
        assertThat(jdbcTemplate.queryForObject(
                "select last_error from outbox_event where event_id = ?",
                String.class,
                "e-stale"
        )).isEqualTo("stuck SENDING recovered");
        assertThat(jdbcTemplate.queryForObject(
                "select next_retry_at from outbox_event where event_id = ?",
                Timestamp.class,
                "e-stale"
        )).isNull();

        assertThat(jdbcTemplate.queryForObject(
                "select status from outbox_event where event_id = ?",
                String.class,
                "e-fresh"
        )).isEqualTo("SENDING");
    }

    @Test
    void cleanupSentShouldDeleteByRetentionAndLimit() {
        Instant now = Instant.now();
        Timestamp oldCreatedAt = Timestamp.from(now.minus(Duration.ofDays(10)));
        Timestamp newCreatedAt = Timestamp.from(now.minus(Duration.ofDays(1)));

        jdbcTemplate.update(
                "insert into outbox_event(event_id, topic, event_key, payload, status, retry_count, next_retry_at, last_error, created_at, updated_at) values (?,?,?,?,?,?,?,?,?,?)",
                "e-old-1",
                "t",
                "k",
                "{}",
                "SENT",
                0,
                null,
                null,
                oldCreatedAt,
                oldCreatedAt
        );
        jdbcTemplate.update(
                "insert into outbox_event(event_id, topic, event_key, payload, status, retry_count, next_retry_at, last_error, created_at, updated_at) values (?,?,?,?,?,?,?,?,?,?)",
                "e-old-2",
                "t",
                "k",
                "{}",
                "SENT",
                0,
                null,
                null,
                oldCreatedAt,
                oldCreatedAt
        );
        jdbcTemplate.update(
                "insert into outbox_event(event_id, topic, event_key, payload, status, retry_count, next_retry_at, last_error, created_at, updated_at) values (?,?,?,?,?,?,?,?,?,?)",
                "e-new",
                "t",
                "k",
                "{}",
                "SENT",
                0,
                null,
                null,
                newCreatedAt,
                newCreatedAt
        );

        int deleted = outboxEventService.cleanupSent(7, 1);

        assertThat(deleted).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select count(1) from outbox_event where event_id in ('e-old-1','e-old-2')",
                Integer.class
        )).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select count(1) from outbox_event where event_id = 'e-new'",
                Integer.class
        )).isEqualTo(1);
    }
}

