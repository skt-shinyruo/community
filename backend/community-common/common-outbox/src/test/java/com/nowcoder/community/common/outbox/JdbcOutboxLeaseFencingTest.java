package com.nowcoder.community.common.outbox;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcOutboxLeaseFencingTest {

    private EmbeddedDatabase database;
    private JdbcTemplate jdbcTemplate;
    private JdbcOutboxEventStore store;

    @BeforeEach
    void setUp() {
        database = new EmbeddedDatabaseBuilder()
                .generateUniqueName(true)
                .setType(EmbeddedDatabaseType.H2)
                .build();
        jdbcTemplate = new JdbcTemplate(database);
        createSchema();
        store = new JdbcOutboxEventStore(jdbcTemplate);
    }

    @AfterEach
    void tearDown() {
        database.shutdown();
    }

    @Test
    void expiredLeaseShouldFenceSuccessRetryAndDeadTransitionsBeforeRecoveryRuns() {
        Instant claimedAt = Instant.parse("2026-08-04T02:00:00Z");
        Instant leaseDeadline = claimedAt.plusSeconds(30);
        OutboxLease successLease = enqueueAndClaim("expired-success", claimedAt, leaseDeadline);
        OutboxLease retryLease = enqueueAndClaim("expired-retry", claimedAt, leaseDeadline);
        OutboxLease deadLease = enqueueAndClaim("expired-dead", claimedAt, leaseDeadline);

        assertThat(store.markSucceeded(successLease, leaseDeadline)).isFalse();
        assertThat(store.markFailedAndScheduleRetry(
                retryLease,
                leaseDeadline,
                leaseDeadline.plusSeconds(5),
                "late retry"
        )).isFalse();
        assertThat(store.markDead(deadLease, leaseDeadline, "late dead")).isFalse();

        assertStillProcessing("expired-success", 0);
        assertStillProcessing("expired-retry", 0);
        assertStillProcessing("expired-dead", 0);
        assertThat(store.recoverExpiredLeases(leaseDeadline)).isEqualTo(3);
    }

    @Test
    void activeLeaseShouldAllowSuccessRetryAndDeadTransitions() {
        Instant claimedAt = Instant.parse("2026-08-04T03:00:00Z");
        Instant leaseDeadline = claimedAt.plusSeconds(30);
        Instant transitionTime = leaseDeadline.minusSeconds(1);
        OutboxLease successLease = enqueueAndClaim("active-success", claimedAt, leaseDeadline);
        OutboxLease retryLease = enqueueAndClaim("active-retry", claimedAt, leaseDeadline);
        OutboxLease deadLease = enqueueAndClaim("active-dead", claimedAt, leaseDeadline);

        assertThat(store.markSucceeded(successLease, transitionTime)).isTrue();
        assertThat(store.markFailedAndScheduleRetry(
                retryLease,
                transitionTime,
                leaseDeadline.plusSeconds(5),
                "retry"
        )).isTrue();
        assertThat(store.markDead(deadLease, transitionTime, "dead")).isTrue();

        assertStatus("active-success", OutboxEventStatus.SUCCEEDED, 0);
        assertStatus("active-retry", OutboxEventStatus.PENDING, 1);
        assertStatus("active-dead", OutboxEventStatus.DEAD, 0);
    }

    @Test
    void successTransitionShouldErasePayloadWhileHoldingTheLeaseFence() {
        Instant claimedAt = Instant.parse("2026-08-04T03:30:00Z");
        Instant leaseDeadline = claimedAt.plusSeconds(30);
        assertThat(store.enqueue(
                "password-reset-mail",
                "auth.password-reset.mail",
                "delivery-id",
                "{\"toEmail\":\"alice@example.com\"}"
        )).isTrue();
        OutboxEvent event = store.findDuePending(10, claimedAt).get(0);
        OutboxLease lease = store.tryClaimProcessing(event.id(), leaseDeadline, claimedAt).orElseThrow();

        assertThat(store.markSucceeded(lease, claimedAt.plusSeconds(1))).isTrue();

        assertThat(jdbcTemplate.queryForObject(
                "select payload from outbox_event where event_id = ?",
                String.class,
                "password-reset-mail"
        )).isEmpty();
    }

    @Test
    void terminalDeadTransitionShouldErasePayloadAndKeepReason() {
        Instant claimedAt = Instant.parse("2026-08-04T03:40:00Z");
        Instant leaseDeadline = claimedAt.plusSeconds(30);
        assertThat(store.enqueue(
                "expired-password-reset-mail",
                "auth.password-reset.mail",
                "opaque-delivery-reference",
                "{\"toEmail\":\"alice@example.com\"}"
        )).isTrue();
        OutboxEvent event = store.findDuePending(10, claimedAt).get(0);
        OutboxLease lease = store.tryClaimProcessing(event.id(), leaseDeadline, claimedAt).orElseThrow();

        assertThat(store.markDeadAndScrubPayload(
                lease, claimedAt.plusSeconds(1), "delivery_expired")).isTrue();

        assertThat(jdbcTemplate.queryForMap(
                "select status, payload, last_error from outbox_event where event_id = ?",
                "expired-password-reset-mail"
        )).containsEntry("STATUS", OutboxEventStatus.DEAD)
                .containsEntry("PAYLOAD", "")
                .containsEntry("LAST_ERROR", "delivery_expired");
    }

    @Test
    void renewalShouldExtendOnlyTheActiveTokenAndFenceRecoveryAtTheOldDeadline() {
        Instant claimedAt = Instant.parse("2026-08-04T04:00:00Z");
        Instant originalDeadline = claimedAt.plusSeconds(30);
        Instant renewalTime = claimedAt.plusSeconds(10);
        Instant extendedDeadline = renewalTime.plusSeconds(30);
        OutboxLease lease = enqueueAndClaim("renewed", claimedAt, originalDeadline);
        OutboxLease wrongToken = new OutboxLease(lease.rowId(), UUID.randomUUID());

        assertThat(store.renewLease(wrongToken, renewalTime, extendedDeadline)).isFalse();
        assertThat(store.renewLease(lease, renewalTime, extendedDeadline)).isTrue();
        assertThat(store.renewLease(lease, extendedDeadline, extendedDeadline.plusSeconds(30))).isFalse();

        assertThat(store.recoverExpiredLeases(originalDeadline, 10)).isZero();
        assertStillProcessing("renewed", 0);
        assertThat(store.markSucceeded(lease, originalDeadline)).isTrue();
    }

    @Test
    void recoveryShouldRespectLimitAcrossRepeatedBoundedScans() {
        Instant claimedAt = Instant.parse("2026-08-04T05:00:00Z");
        Instant deadline = claimedAt.plusSeconds(30);
        for (int index = 0; index < 5; index++) {
            enqueueAndClaim("bounded-" + index, claimedAt, deadline);
        }

        assertThat(store.recoverExpiredLeases(deadline, 2)).isEqualTo(2);
        assertStatusCount(OutboxEventStatus.PENDING, 2);
        assertStatusCount(OutboxEventStatus.PROCESSING, 3);

        assertThat(store.recoverExpiredLeases(deadline, 2)).isEqualTo(2);
        assertThat(store.recoverExpiredLeases(deadline, 2)).isEqualTo(1);
        assertStatusCount(OutboxEventStatus.PENDING, 5);
        assertStatusCount(OutboxEventStatus.PROCESSING, 0);
    }

    private OutboxLease enqueueAndClaim(String eventId, Instant claimedAt, Instant leaseDeadline) {
        assertThat(store.enqueue(eventId, "projection.points", eventId, "{}")).isTrue();
        OutboxEvent event = store.findDuePending(10, claimedAt).stream()
                .filter(candidate -> eventId.equals(candidate.eventId()))
                .findFirst()
                .orElseThrow();
        return store.tryClaimProcessing(event.id(), leaseDeadline, claimedAt).orElseThrow();
    }

    private void assertStillProcessing(String eventId, int retryCount) {
        assertStatus(eventId, OutboxEventStatus.PROCESSING, retryCount);
    }

    private void assertStatus(String eventId, String status, int retryCount) {
        assertThat(jdbcTemplate.queryForObject(
                "select status from outbox_event where event_id = ?",
                String.class,
                eventId
        )).isEqualTo(status);
        assertThat(jdbcTemplate.queryForObject(
                "select retry_count from outbox_event where event_id = ?",
                Integer.class,
                eventId
        )).isEqualTo(retryCount);
    }

    private void assertStatusCount(String status, int expected) {
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from outbox_event where status = ?",
                Integer.class,
                status
        )).isEqualTo(expected);
    }

    private void createSchema() {
        jdbcTemplate.execute("""
                create table outbox_event (
                  id binary(16) primary key,
                  event_id varchar(64) not null unique,
                  topic varchar(255) not null,
                  event_key varchar(255) not null,
                  payload clob not null,
                  status varchar(32) not null,
                  lease_token binary(16),
                  processing_lease_until timestamp,
                  retry_count int not null default 0,
                  next_retry_at timestamp,
                  last_error varchar(512),
                  trace_id varchar(32),
                  traceparent varchar(128),
                  created_at timestamp default current_timestamp,
                  updated_at timestamp default current_timestamp
                )
                """);
    }
}
