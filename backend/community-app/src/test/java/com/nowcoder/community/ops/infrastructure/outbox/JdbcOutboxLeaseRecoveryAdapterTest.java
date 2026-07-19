package com.nowcoder.community.ops.infrastructure.outbox;

import com.nowcoder.community.common.id.BinaryUuidCodec;
import com.nowcoder.community.common.id.UuidV7Generator;
import com.nowcoder.community.common.outbox.JdbcOutboxEventStore;
import com.nowcoder.community.common.outbox.OutboxEventStatus;
import com.nowcoder.community.common.outbox.OutboxLease;
import com.nowcoder.community.ops.application.result.OutboxLeaseRecoveryResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcOutboxLeaseRecoveryAdapterTest {

    private static final Instant NOW = Instant.parse("2026-07-20T03:00:00Z");

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
                new UuidV7Generator(Clock.fixed(NOW.minusSeconds(60), ZoneOffset.UTC))
        );
    }

    @AfterEach
    void tearDown() {
        database.shutdown();
    }

    @Test
    void recoveryShouldClampLimitToAtLeastOne() {
        UUID firstId = UUID.fromString("01982900-0000-7000-8000-000000000001");
        UUID secondId = UUID.fromString("01982900-0000-7000-8000-000000000002");
        insertProcessing(firstId, firstId, NOW.minusSeconds(1), NOW.minusSeconds(30));
        insertProcessing(secondId, secondId, NOW.minusSeconds(1), NOW.minusSeconds(30));
        JdbcOutboxLeaseRecoveryAdapter adapter = adapter(jdbcTemplate);

        OutboxLeaseRecoveryResult result = adapter.recoverExpiredLeases(0);

        assertThat(result.selectedCount()).isEqualTo(1);
        assertThat(result.recoveredCount()).isEqualTo(1);
        assertThat(countStatus(OutboxEventStatus.PENDING)).isEqualTo(1);
        assertThat(countStatus(OutboxEventStatus.PROCESSING)).isEqualTo(1);
    }

    @Test
    void recoveryShouldClampLimitToFiveHundred() {
        List<Object[]> rows = new ArrayList<>();
        for (int index = 0; index < 501; index++) {
            UUID id = new UUID(0x0198_2900_0000_7000L + index, 0x8000_0000_0000_0000L + index);
            rows.add(processingRow(id, id, "bulk-" + index, NOW.minusSeconds(1), NOW.minusSeconds(30)));
        }
        jdbcTemplate.batchUpdate(
                """
                        insert into outbox_event(
                          id, event_id, topic, event_key, payload, status, lease_token,
                          processing_lease_until, retry_count, next_retry_at, last_error
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?)
                        """,
                rows
        );
        JdbcOutboxLeaseRecoveryAdapter adapter = adapter(jdbcTemplate);

        OutboxLeaseRecoveryResult result = adapter.recoverExpiredLeases(5_000);

        assertThat(result.selectedCount()).isEqualTo(500);
        assertThat(result.recoveredCount()).isEqualTo(500);
        assertThat(countStatus(OutboxEventStatus.PENDING)).isEqualTo(500);
        assertThat(countStatus(OutboxEventStatus.PROCESSING)).isEqualTo(1);
    }

    @Test
    void dueRetryTimestampShouldNotRecoverAnUnexpiredProcessingLease() {
        UUID rowId = UUID.fromString("01982900-0000-7000-8000-000000000011");
        UUID leaseToken = UUID.fromString("01982900-0000-7000-8000-000000000012");
        insertProcessing(rowId, leaseToken, NOW.plusSeconds(1), NOW.minusSeconds(30));
        LeaseState before = readState(rowId);

        OutboxLeaseRecoveryResult result = adapter(jdbcTemplate).recoverExpiredLeases(10);

        assertThat(result.selectedCount()).isZero();
        assertThat(result.recoveredCount()).isZero();
        assertThat(readState(rowId)).isEqualTo(before);
    }

    @Test
    void recoveredLeaseShouldRemainFencedAfterSharedStoreReclaimsIt() {
        UUID rowId = UUID.fromString("01982900-0000-7000-8000-000000000021");
        insertPending(rowId, "adapter-reclaim");
        OutboxLease leaseA = store.tryClaimProcessing(rowId, NOW, NOW.minusSeconds(30)).orElseThrow();

        OutboxLeaseRecoveryResult result = adapter(jdbcTemplate).recoverExpiredLeases(10);
        OutboxLease leaseB = store.tryClaimProcessing(rowId, NOW.plusSeconds(30), NOW).orElseThrow();
        LeaseState heldByB = readState(rowId);

        assertThat(result.selectedCount()).isEqualTo(1);
        assertThat(result.recoveredCount()).isEqualTo(1);
        assertThat(leaseB.token()).isNotEqualTo(leaseA.token());
        assertThat(store.markSucceeded(leaseA, NOW.plusSeconds(1))).isFalse();
        assertThat(readState(rowId)).isEqualTo(heldByB);
        assertThat(store.markFailedAndScheduleRetry(
                leaseA,
                NOW.plusSeconds(1),
                NOW.plusSeconds(10),
                "stale retry"
        )).isFalse();
        assertThat(readState(rowId)).isEqualTo(heldByB);
        assertThat(store.markDead(leaseA, NOW.plusSeconds(1), "stale dead")).isFalse();
        assertThat(readState(rowId)).isEqualTo(heldByB);
    }

    @Test
    void recoveryUpdateShouldRecheckDeadlineAfterSelection() {
        UUID rowId = UUID.fromString("01982900-0000-7000-8000-000000000031");
        insertPending(rowId, "adapter-race");
        store.tryClaimProcessing(rowId, NOW, NOW.minusSeconds(30)).orElseThrow();
        AtomicReference<OutboxLease> leaseB = new AtomicReference<>();
        AfterQueryJdbcTemplate racingTemplate = new AfterQueryJdbcTemplate(database, () -> {
            int released = jdbcTemplate.update(
                    """
                            update outbox_event
                            set status = ?, next_retry_at = ?, lease_token = null,
                                processing_lease_until = null, updated_at = ?
                            where id = ? and status = ? and processing_lease_until <= ?
                            """,
                    OutboxEventStatus.PENDING,
                    Timestamp.from(NOW),
                    Timestamp.from(NOW),
                    BinaryUuidCodec.toBytes(rowId),
                    OutboxEventStatus.PROCESSING,
                    Timestamp.from(NOW)
            );
            assertThat(released).isEqualTo(1);
            leaseB.set(store.tryClaimProcessing(rowId, NOW.plusSeconds(30), NOW).orElseThrow());
        });

        OutboxLeaseRecoveryResult result = adapter(racingTemplate).recoverExpiredLeases(10);
        LeaseState state = readState(rowId);

        assertThat(result.selectedCount()).isEqualTo(1);
        assertThat(result.recoveredCount()).isZero();
        assertThat(state.status()).isEqualTo(OutboxEventStatus.PROCESSING);
        assertThat(state.leaseToken()).isEqualTo(leaseB.get().token());
        assertThat(state.leaseUntil()).isEqualTo(NOW.plusSeconds(30));
        assertThat(state.nextRetryAt()).isNull();
    }

    private JdbcOutboxLeaseRecoveryAdapter adapter(JdbcTemplate template) {
        return new JdbcOutboxLeaseRecoveryAdapter(template, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private void insertPending(UUID id, String eventId) {
        jdbcTemplate.update(
                """
                        insert into outbox_event(
                          id, event_id, topic, event_key, payload, status, lease_token,
                          processing_lease_until, retry_count, next_retry_at, last_error
                        ) values (?, ?, ?, ?, ?, ?, null, null, 0, ?, ?)
                        """,
                BinaryUuidCodec.toBytes(id),
                eventId,
                "projection.points",
                id.toString(),
                "{}",
                OutboxEventStatus.PENDING,
                Timestamp.from(NOW.minusSeconds(30)),
                "old error"
        );
    }

    private void insertProcessing(UUID id, UUID token, Instant leaseUntil, Instant nextRetryAt) {
        jdbcTemplate.update(
                """
                        insert into outbox_event(
                          id, event_id, topic, event_key, payload, status, lease_token,
                          processing_lease_until, retry_count, next_retry_at, last_error
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?)
                        """,
                processingRow(id, token, id.toString(), leaseUntil, nextRetryAt)
        );
    }

    private Object[] processingRow(
            UUID id,
            UUID token,
            String eventId,
            Instant leaseUntil,
            Instant nextRetryAt
    ) {
        return new Object[]{
                BinaryUuidCodec.toBytes(id),
                eventId,
                "projection.points",
                id.toString(),
                "{}",
                OutboxEventStatus.PROCESSING,
                BinaryUuidCodec.toBytes(token),
                Timestamp.from(leaseUntil),
                nextRetryAt == null ? null : Timestamp.from(nextRetryAt),
                "old error"
        };
    }

    private int countStatus(String status) {
        return jdbcTemplate.queryForObject(
                "select count(*) from outbox_event where status = ?",
                Integer.class,
                status
        );
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
                          event_id varchar(128) not null,
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

    private record LeaseState(
            String status,
            int retryCount,
            Instant nextRetryAt,
            String lastError,
            UUID leaseToken,
            Instant leaseUntil
    ) {
    }

    private static final class AfterQueryJdbcTemplate extends JdbcTemplate {

        private final Runnable afterQuery;
        private boolean invoked;

        private AfterQueryJdbcTemplate(EmbeddedDatabase database, Runnable afterQuery) {
            super(database);
            this.afterQuery = afterQuery;
        }

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            List<T> rows = super.query(sql, rowMapper, args);
            if (!invoked) {
                invoked = true;
                afterQuery.run();
            }
            return rows;
        }
    }
}
