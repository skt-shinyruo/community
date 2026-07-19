package com.nowcoder.community.common.outbox;

import com.nowcoder.community.common.id.BinaryUuidCodec;
import com.nowcoder.community.common.id.UuidV7Generator;
import com.nowcoder.community.common.trace.TraceContext;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Scope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class JdbcOutboxEventStoreTest {

    private static final Instant TOKEN_TIME = Instant.parse("2026-03-14T00:00:00Z");

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
        createOutboxSchema(jdbcTemplate);
        store = new JdbcOutboxEventStore(
                jdbcTemplate,
                new UuidV7Generator(Clock.fixed(TOKEN_TIME, ZoneOffset.UTC))
        );
    }

    @AfterEach
    void tearDown() {
        TraceContext.clear();
        database.shutdown();
    }

    @Test
    void leaseShouldRequireRowIdAndOpaqueToken() {
        UUID rowId = UUID.fromString("01965429-b34a-7000-8000-000000000001");
        UUID token = UUID.fromString("01965429-b34a-7000-8000-000000000002");

        assertThat(new OutboxLease(rowId, token))
                .isEqualTo(new OutboxLease(rowId, token));
        assertThatNullPointerException()
                .isThrownBy(() -> new OutboxLease(null, token))
                .withMessage("rowId");
        assertThatNullPointerException()
                .isThrownBy(() -> new OutboxLease(rowId, null))
                .withMessage("token");
    }

    @Test
    void publicEventRecordsShouldNotExposeLeaseOwnership() {
        List<String> eventComponents = Arrays.stream(OutboxEvent.class.getRecordComponents())
                .map(component -> component.getName())
                .toList();
        List<String> viewComponents = Arrays.stream(OutboxEventView.class.getRecordComponents())
                .map(component -> component.getName())
                .toList();

        assertThat(eventComponents)
                .doesNotContain("leaseToken", "processingLeaseUntil")
                .containsExactly(
                        "id",
                        "eventId",
                        "topic",
                        "eventKey",
                        "payload",
                        "status",
                        "retryCount",
                        "nextRetryAt",
                        "lastError",
                        "traceId",
                        "traceparent"
                );
        assertThat(viewComponents)
                .doesNotContain("leaseToken", "processingLeaseUntil")
                .containsExactly(
                        "id",
                        "eventId",
                        "topic",
                        "eventKey",
                        "payload",
                        "status",
                        "retryCount",
                        "nextRetryAt",
                        "lastError",
                        "traceId",
                        "traceparent",
                        "createdAt",
                        "updatedAt"
                );
    }

    @Test
    void enqueueAndClaimShouldStoreLeaseOutsideRetrySchedule() {
        Instant now = TOKEN_TIME;
        SpanContext spanContext = SpanContext.create(
                "dddddddddddddddddddddddddddddddd",
                "1234567890abcdef",
                TraceFlags.getSampled(),
                TraceState.getDefault()
        );

        boolean inserted;
        try (Scope ignored = Span.wrap(spanContext).makeCurrent()) {
            inserted = store.enqueue(
                    "e-1:points",
                    "projection.points",
                    "1",
                    "{\"userId\":1,\"delta\":10}"
            );
        }
        assertThat(inserted).isTrue();

        List<OutboxEvent> due = store.findDuePending(10, now);
        assertThat(due).hasSize(1);
        OutboxEvent event = due.get(0);
        assertThat(event.id()).isNotNull();
        assertThat(event.id().version()).isEqualTo(7);
        assertThat(event.eventId()).isEqualTo("e-1:points");
        assertThat(event.topic()).isEqualTo("projection.points");
        assertThat(event.status()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(event.traceId()).isEqualTo("dddddddddddddddddddddddddddddddd");
        assertThat(event.traceparent()).isEqualTo("00-dddddddddddddddddddddddddddddddd-1234567890abcdef-01");
        assertThat(readState(event.id()).leaseToken()).isNull();
        assertThat(readState(event.id()).leaseUntil()).isNull();

        Instant leaseUntil = now.plusSeconds(30);
        OutboxLease lease = store.tryClaimProcessing(event.id(), leaseUntil, now).orElseThrow();

        assertThat(lease.rowId()).isEqualTo(event.id());
        assertThat(lease.token()).isNotEqualTo(event.id());
        LeaseState claimed = readState(event.id());
        assertThat(claimed.status()).isEqualTo(OutboxEventStatus.PROCESSING);
        assertThat(claimed.leaseToken()).isEqualTo(lease.token());
        assertThat(claimed.leaseUntil()).isEqualTo(leaseUntil);
        assertThat(claimed.nextRetryAt()).isNull();
        assertThat(Arrays.stream(OutboxEvent.class.getRecordComponents()))
                .extracting(component -> component.getName())
                .doesNotContain("leaseToken", "processingLeaseUntil");
    }

    @Test
    void recoveryShouldOnlyReleaseLeasesWhoseDeadlineHasArrived() {
        Instant now = TOKEN_TIME.plusSeconds(60);
        UUID expiredId = UUID.fromString("01965429-b34a-7000-8000-000000000011");
        UUID activeId = UUID.fromString("01965429-b34a-7000-8000-000000000012");
        insertPending(expiredId, "expired", 2, now.minusSeconds(5), "expired-old-error");
        insertPending(activeId, "active", 3, now.minusSeconds(5), "active-old-error");
        OutboxLease expiredLease = store.tryClaimProcessing(expiredId, now, now.minusSeconds(30)).orElseThrow();
        OutboxLease activeLease = store.tryClaimProcessing(activeId, now.plusSeconds(1), now.minusSeconds(30)).orElseThrow();

        int recovered = store.recoverExpiredLeases(now);

        assertThat(recovered).isEqualTo(1);
        assertThat(readState(expiredId)).isEqualTo(new LeaseState(
                OutboxEventStatus.PENDING,
                2,
                now,
                "expired-old-error",
                null,
                null
        ));
        assertThat(readState(activeId)).isEqualTo(new LeaseState(
                OutboxEventStatus.PROCESSING,
                3,
                null,
                "active-old-error",
                activeLease.token(),
                now.plusSeconds(1)
        ));
        assertThat(expiredLease.token()).isNotEqualTo(activeLease.token());
    }

    @Test
    void staleSuccessShouldNotOverwriteReclaimedLeaseButCurrentSuccessShouldCompleteIt() {
        Instant recoveryTime = TOKEN_TIME.plusSeconds(60);
        UUID rowId = UUID.fromString("01965429-b34a-7000-8000-000000000021");
        insertPending(rowId, "success", 2, recoveryTime.minusSeconds(10), "old error");
        LeasePair leases = expireRecoverAndReclaim(rowId, recoveryTime);
        LeaseState heldByB = readState(rowId);

        assertThat(store.markSucceeded(leases.a(), recoveryTime.plusSeconds(1))).isFalse();
        assertState(rowId, heldByB);

        assertThat(store.markSucceeded(leases.b(), recoveryTime.plusSeconds(2))).isTrue();
        assertThat(readState(rowId)).isEqualTo(new LeaseState(
                OutboxEventStatus.SUCCEEDED,
                2,
                null,
                null,
                null,
                null
        ));
    }

    @Test
    void staleRetryShouldNotOverwriteReclaimedLeaseButCurrentRetryShouldScheduleIt() {
        Instant recoveryTime = TOKEN_TIME.plusSeconds(120);
        UUID rowId = UUID.fromString("01965429-b34a-7000-8000-000000000022");
        insertPending(rowId, "retry", 4, recoveryTime.minusSeconds(10), "old error");
        LeasePair leases = expireRecoverAndReclaim(rowId, recoveryTime);
        LeaseState heldByB = readState(rowId);

        assertThat(store.markFailedAndScheduleRetry(
                leases.a(),
                recoveryTime.plusSeconds(1),
                recoveryTime.plusSeconds(20),
                "stale retry"
        )).isFalse();
        assertState(rowId, heldByB);

        Instant nextRetryAt = recoveryTime.plusSeconds(30);
        assertThat(store.markFailedAndScheduleRetry(
                leases.b(),
                recoveryTime.plusSeconds(2),
                nextRetryAt,
                "current retry"
        )).isTrue();
        assertThat(readState(rowId)).isEqualTo(new LeaseState(
                OutboxEventStatus.PENDING,
                5,
                nextRetryAt,
                "current retry",
                null,
                null
        ));
    }

    @Test
    void staleDeadShouldNotOverwriteReclaimedLeaseButCurrentDeadShouldCompleteIt() {
        Instant recoveryTime = TOKEN_TIME.plusSeconds(180);
        UUID rowId = UUID.fromString("01965429-b34a-7000-8000-000000000023");
        insertPending(rowId, "dead", 6, recoveryTime.minusSeconds(10), "old error");
        LeasePair leases = expireRecoverAndReclaim(rowId, recoveryTime);
        LeaseState heldByB = readState(rowId);

        assertThat(store.markDead(leases.a(), recoveryTime.plusSeconds(1), "stale dead")).isFalse();
        assertState(rowId, heldByB);

        assertThat(store.markDead(leases.b(), recoveryTime.plusSeconds(2), "current dead")).isTrue();
        assertThat(readState(rowId)).isEqualTo(new LeaseState(
                OutboxEventStatus.DEAD,
                6,
                null,
                "current dead",
                null,
                null
        ));
    }

    private LeasePair expireRecoverAndReclaim(UUID rowId, Instant recoveryTime) {
        LeaseState pending = readState(rowId);
        assertThat(pending.status()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(pending.nextRetryAt()).isBefore(recoveryTime);
        assertThat(pending.leaseToken()).isNull();
        assertThat(pending.leaseUntil()).isNull();

        OutboxLease leaseA = store.tryClaimProcessing(
                rowId,
                recoveryTime,
                recoveryTime.minusSeconds(30)
        ).orElseThrow();
        assertState(rowId, new LeaseState(
                OutboxEventStatus.PROCESSING,
                pending.retryCount(),
                null,
                pending.lastError(),
                leaseA.token(),
                recoveryTime
        ));

        assertThat(store.recoverExpiredLeases(recoveryTime)).isEqualTo(1);
        assertState(rowId, new LeaseState(
                OutboxEventStatus.PENDING,
                pending.retryCount(),
                recoveryTime,
                pending.lastError(),
                null,
                null
        ));

        OutboxLease leaseB = store.tryClaimProcessing(
                rowId,
                recoveryTime.plusSeconds(30),
                recoveryTime
        ).orElseThrow();
        assertThat(leaseB.token()).isNotEqualTo(leaseA.token());
        assertState(rowId, new LeaseState(
                OutboxEventStatus.PROCESSING,
                pending.retryCount(),
                null,
                pending.lastError(),
                leaseB.token(),
                recoveryTime.plusSeconds(30)
        ));
        return new LeasePair(leaseA, leaseB);
    }

    private void insertPending(
            UUID id,
            String eventId,
            int retryCount,
            Instant nextRetryAt,
            String lastError
    ) {
        jdbcTemplate.update(
                """
                        insert into outbox_event(
                          id, event_id, topic, event_key, payload, status, lease_token,
                          processing_lease_until, retry_count, next_retry_at, last_error,
                          trace_id, traceparent
                        ) values (?, ?, ?, ?, ?, ?, null, null, ?, ?, ?, null, null)
                        """,
                BinaryUuidCodec.toBytes(id),
                eventId,
                "projection.points",
                id.toString(),
                "{}",
                OutboxEventStatus.PENDING,
                retryCount,
                nextRetryAt == null ? null : Timestamp.from(nextRetryAt),
                lastError
        );
    }

    private void assertState(UUID id, LeaseState expected) {
        LeaseState actual = readState(id);
        assertThat(actual.status()).isEqualTo(expected.status());
        assertThat(actual.retryCount()).isEqualTo(expected.retryCount());
        assertThat(actual.nextRetryAt()).isEqualTo(expected.nextRetryAt());
        assertThat(actual.lastError()).isEqualTo(expected.lastError());
        assertThat(actual.leaseToken()).isEqualTo(expected.leaseToken());
        assertThat(actual.leaseUntil()).isEqualTo(expected.leaseUntil());
    }

    private LeaseState readState(UUID id) {
        return jdbcTemplate.queryForObject(
                """
                        select status, retry_count, next_retry_at, last_error,
                               lease_token, processing_lease_until
                        from outbox_event where id = ?
                        """,
                (rs, rowNum) -> {
                    Timestamp nextRetryAt = rs.getTimestamp("next_retry_at");
                    Timestamp leaseUntil = rs.getTimestamp("processing_lease_until");
                    byte[] leaseToken = rs.getBytes("lease_token");
                    return new LeaseState(
                            rs.getString("status"),
                            rs.getInt("retry_count"),
                            nextRetryAt == null ? null : nextRetryAt.toInstant(),
                            rs.getString("last_error"),
                            leaseToken == null ? null : BinaryUuidCodec.fromBytes(leaseToken),
                            leaseUntil == null ? null : leaseUntil.toInstant()
                    );
                },
                BinaryUuidCodec.toBytes(id)
        );
    }

    private static void createOutboxSchema(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute(
                """
                        create table outbox_event (
                          id binary(16) primary key,
                          event_id varchar(64) not null,
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
                          updated_at timestamp default current_timestamp,
                          constraint uk_outbox_event_id unique (event_id)
                        )
                        """
        );
        jdbcTemplate.execute("create index idx_outbox_status_next on outbox_event(status, next_retry_at, id)");
        jdbcTemplate.execute(
                "create index idx_outbox_processing_lease on outbox_event(status, processing_lease_until, id)"
        );
    }

    private record LeasePair(OutboxLease a, OutboxLease b) {
    }

    private record LeaseState(
            String status,
            int retryCount,
            Instant nextRetryAt,
            String lastError,
            UUID leaseToken,
            Instant leaseUntil
    ) {
    }
}
