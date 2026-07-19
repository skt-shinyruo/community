package com.nowcoder.community.im.core.infrastructure.persistence;

import com.nowcoder.community.common.id.BinaryUuidCodec;
import com.nowcoder.community.common.outbox.JdbcOutboxEventStore;
import com.nowcoder.community.common.outbox.OutboxEventStatus;
import com.nowcoder.community.common.outbox.OutboxLease;
import com.nowcoder.community.im.common.command.SendPrivateTextCommand;
import com.nowcoder.community.im.common.command.SendRoomTextCommand;
import com.nowcoder.community.im.common.policy.PrivateMessagePolicyDecision;
import com.nowcoder.community.im.core.application.ConversationApplicationService;
import com.nowcoder.community.im.core.application.PrivateMessageApplicationService;
import com.nowcoder.community.im.core.application.RoomApplicationService;
import com.nowcoder.community.im.core.application.RoomMessageApplicationService;
import com.nowcoder.community.im.core.application.UnreadApplicationService;
import com.nowcoder.community.im.core.domain.repository.ConversationReadStateRepository;
import com.nowcoder.community.im.core.domain.repository.PrivateMessageRepository;
import com.nowcoder.community.im.core.domain.repository.RoomMemberRepository;
import com.nowcoder.community.im.core.domain.repository.RoomMessageRepository;
import com.nowcoder.community.im.core.domain.repository.RoomReadStateRepository;
import com.nowcoder.community.im.core.policy.PrivateMessagePolicyVerifier;
import com.nowcoder.community.im.core.support.ConversationIdSupport;
import com.nowcoder.community.im.migration.ImMigrationRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "spring.sql.init.mode=never",
        "spring.flyway.enabled=false"
})
@ActiveProfiles("test")
@Testcontainers
@Transactional
class ImCoreMySqlMigrationRepositoryContractTest {

    @Container
    private static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.0")
            .withDatabaseName("im_core_repository_contract")
            .withUsername("im_core_migrator")
            .withPassword("im_core_migrator");

    private static boolean schemaMigrated;

    @Autowired
    private RoomApplicationService roomApplicationService;

    @Autowired
    private RoomMessageApplicationService roomMessageApplicationService;

    @Autowired
    private RoomMemberRepository roomMemberRepository;

    @Autowired
    private RoomMessageRepository roomMessageRepository;

    @Autowired
    private RoomReadStateRepository roomReadStateRepository;

    @Autowired
    private PrivateMessageApplicationService privateMessageApplicationService;

    @Autowired
    private PrivateMessageRepository privateMessageRepository;

    @Autowired
    private ConversationReadStateRepository conversationReadStateRepository;

    @Autowired
    private ConversationApplicationService conversationApplicationService;

    @Autowired
    private UnreadApplicationService unreadApplicationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JdbcOutboxEventStore outboxEventStore;

    @MockBean
    private PrivateMessagePolicyVerifier privateMessagePolicyVerifier;

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", ImCoreMySqlMigrationRepositoryContractTest::migratedJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    }

    private static synchronized String migratedJdbcUrl() {
        if (!schemaMigrated) {
            ImMigrationRunner.standard(
                    MYSQL.getJdbcUrl(),
                    MYSQL.getUsername(),
                    MYSQL.getPassword()
            ).migrate();
            schemaMigrated = true;
        }
        return MYSQL.getJdbcUrl();
    }

    @BeforeEach
    void allowPrivateMessages() {
        when(privateMessagePolicyVerifier.verify(any(UUID.class), any(UUID.class)))
                .thenReturn(PrivateMessagePolicyDecision.allow());
    }

    @Test
    void migrationRunnerShouldOwnTheSchemaBeforeRuntimeRepositoriesStart() {
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from im_core_schema_history where version = '001' and success = 1",
                Integer.class
        )).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from im_core_schema_history where version = '002' and success = 1",
                Integer.class
        )).isEqualTo(1);
        assertThat(jdbcTemplate.getDataSource()).isNotNull();
        assertThat(jdbcTemplate.execute((ConnectionCallback<String>)
                connection -> connection.getMetaData().getDatabaseProductName()))
                .isEqualTo("MySQL");
    }

    @Test
    void outboxAutoConfigurationShouldProvideTheSharedJdbcStore() {
        assertThat(outboxEventStore).isExactlyInstanceOf(JdbcOutboxEventStore.class);
    }

    @Test
    void staleSuccessShouldMissAfterExpiredLeaseIsReclaimedOnMigratedMySqlSchema() {
        Instant recoveryTime = Instant.parse("2026-07-20T01:00:00Z");
        UUID rowId = enqueuePending(
                "mysql-stale-success",
                2,
                recoveryTime.minusSeconds(10),
                "existing success error"
        );
        LeasePair leases = expireRecoverAndReclaim(rowId, recoveryTime);
        LeaseState heldByB = readState(rowId);

        assertThat(outboxEventStore.markSucceeded(leases.a(), recoveryTime.plusSeconds(1))).isFalse();
        assertState(rowId, heldByB);
    }

    @Test
    void staleRetryShouldMissAfterExpiredLeaseIsReclaimedOnMigratedMySqlSchema() {
        Instant recoveryTime = Instant.parse("2026-07-20T02:00:00Z");
        UUID rowId = enqueuePending(
                "mysql-stale-retry",
                4,
                recoveryTime.minusSeconds(10),
                "existing retry error"
        );
        LeasePair leases = expireRecoverAndReclaim(rowId, recoveryTime);
        LeaseState heldByB = readState(rowId);

        assertThat(outboxEventStore.markFailedAndScheduleRetry(
                leases.a(),
                recoveryTime.plusSeconds(1),
                recoveryTime.plusSeconds(20),
                "stale retry"
        )).isFalse();
        assertState(rowId, heldByB);
    }

    @Test
    void staleDeadShouldMissAfterExpiredLeaseIsReclaimedOnMigratedMySqlSchema() {
        Instant recoveryTime = Instant.parse("2026-07-20T03:00:00Z");
        UUID rowId = enqueuePending(
                "mysql-stale-dead",
                6,
                recoveryTime.minusSeconds(10),
                "existing dead error"
        );
        LeasePair leases = expireRecoverAndReclaim(rowId, recoveryTime);
        LeaseState heldByB = readState(rowId);

        assertThat(outboxEventStore.markDead(leases.a(), recoveryTime.plusSeconds(1), "stale dead")).isFalse();
        assertState(rowId, heldByB);
    }

    @Test
    void migratedSchemaShouldSupportRoomSequenceMembershipMessageWatermarkInboxAndOutbox() {
        UUID sender = uuid(101);
        UUID receiver = uuid(102);
        UUID roomId = roomApplicationService.createRoom(sender, "mysql-room").roomId();
        roomApplicationService.joinRoom(receiver, roomId);

        var first = roomMessageApplicationService.persist(new SendRoomTextCommand(
                "mysql-room-request-1",
                "mysql-room-client-1",
                sender,
                roomId,
                "first room message",
                System.currentTimeMillis()
        ));
        var second = roomMessageApplicationService.persist(new SendRoomTextCommand(
                "mysql-room-request-2",
                "mysql-room-client-2",
                sender,
                roomId,
                "second room message",
                System.currentTimeMillis()
        ));

        assertThat(first.seq()).isEqualTo(1L);
        assertThat(second.seq()).isEqualTo(2L);
        assertThat(roomMemberRepository.isMember(roomId, sender)).isTrue();
        assertThat(roomMemberRepository.isMember(roomId, receiver)).isTrue();
        assertThat(roomMemberRepository.countMembers(roomId)).isEqualTo(2);
        assertThat(roomMemberRepository.currentMembershipProjectionVersion()).isEqualTo(2L);
        assertThat(roomMessageRepository.listAfterSeq(roomId, 0L, 10))
                .extracting(message -> message.content())
                .containsExactly("first room message", "second room message");
        assertThat(roomReadStateRepository.getLastReadSeq(roomId, sender)).isEqualTo(2L);

        roomApplicationService.markRead(receiver, roomId, 1L);

        assertThat(roomReadStateRepository.getLastReadSeq(roomId, receiver)).isEqualTo(1L);
        assertThat(unreadApplicationService.summary(receiver, 10).rooms())
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.roomId()).isEqualTo(roomId);
                    assertThat(item.lastSeq()).isEqualTo(2L);
                    assertThat(item.lastReadSeq()).isEqualTo(1L);
                    assertThat(item.unreadCount()).isEqualTo(1L);
                });
        assertThat(outboxCount("im.event.room-persisted")).isEqualTo(2);
        assertThat(outboxCount("im.event.room-committed")).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from im_membership_version_log where room_id = ?",
                Integer.class,
                uuidBytes(roomId)
        )).isEqualTo(2);
    }

    @Test
    void migratedSchemaShouldSupportPrivateSequenceMessageWatermarkInboxAndOutbox() {
        UUID sender = uuid(201);
        UUID receiver = uuid(202);
        String conversationId = ConversationIdSupport.conversationId(sender, receiver);

        var first = privateMessageApplicationService.persist(new SendPrivateTextCommand(
                "mysql-private-request-1",
                "mysql-private-client-1",
                sender,
                receiver,
                conversationId,
                "first private message",
                System.currentTimeMillis()
        ));
        var second = privateMessageApplicationService.persist(new SendPrivateTextCommand(
                "mysql-private-request-2",
                "mysql-private-client-2",
                sender,
                receiver,
                conversationId,
                "second private message",
                System.currentTimeMillis()
        ));

        assertThat(first.seq()).isEqualTo(1L);
        assertThat(second.seq()).isEqualTo(2L);
        assertThat(privateMessageRepository.listAfterSeq(conversationId, 0L, 10))
                .extracting(message -> message.content())
                .containsExactly("first private message", "second private message");
        assertThat(conversationReadStateRepository.getLastReadSeq(conversationId, sender))
                .isEqualTo(2L);
        assertThat(conversationApplicationService.listConversations(receiver, 0, 10))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.conversationId()).isEqualTo(conversationId);
                    assertThat(item.lastSeq()).isEqualTo(2L);
                    assertThat(item.lastReadSeq()).isZero();
                    assertThat(item.unreadCount()).isEqualTo(2L);
                    assertThat(item.lastMessage().content()).isEqualTo("second private message");
                });

        conversationApplicationService.markRead(receiver, conversationId, 1L);

        assertThat(conversationReadStateRepository.getLastReadSeq(conversationId, receiver))
                .isEqualTo(1L);
        assertThat(unreadApplicationService.summary(receiver, 10).conversations())
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.conversationId()).isEqualTo(conversationId);
                    assertThat(item.lastSeq()).isEqualTo(2L);
                    assertThat(item.lastReadSeq()).isEqualTo(1L);
                    assertThat(item.unreadCount()).isEqualTo(1L);
                });
        assertThat(outboxCount("im.event.private-persisted")).isEqualTo(2);
        assertThat(outboxCount("im.event.private-committed")).isEqualTo(2);
    }

    private int outboxCount(String topic) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from outbox_event where topic = ?",
                Integer.class,
                topic
        );
        return count == null ? 0 : count;
    }

    private LeasePair expireRecoverAndReclaim(UUID rowId, Instant recoveryTime) {
        LeaseState pending = readState(rowId);
        assertThat(pending.status()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(pending.nextRetryAt()).isBefore(recoveryTime);
        assertThat(pending.leaseToken()).isNull();
        assertThat(pending.leaseUntil()).isNull();

        OutboxLease leaseA = outboxEventStore.tryClaimProcessing(
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

        assertThat(outboxEventStore.recoverExpiredLeases(recoveryTime)).isEqualTo(1);
        assertState(rowId, new LeaseState(
                OutboxEventStatus.PENDING,
                pending.retryCount(),
                recoveryTime,
                pending.lastError(),
                null,
                null
        ));

        OutboxLease leaseB = outboxEventStore.tryClaimProcessing(
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

    private UUID enqueuePending(
            String eventId,
            int retryCount,
            Instant nextRetryAt,
            String lastError
    ) {
        assertThat(outboxEventStore.enqueue(
                eventId,
                "im.event.private-persisted",
                eventId,
                "{}"
        )).isTrue();
        UUID id = jdbcTemplate.queryForObject(
                "select id from outbox_event where event_id = ?",
                (rs, rowNum) -> BinaryUuidCodec.fromBytes(rs.getBytes("id")),
                eventId
        );
        assertThat(id).isNotNull();
        assertState(id, new LeaseState(
                OutboxEventStatus.PENDING,
                0,
                null,
                null,
                null,
                null
        ));

        jdbcTemplate.update(
                """
                        update outbox_event
                        set retry_count = ?, next_retry_at = ?, last_error = ?
                        where id = ?
                        """,
                retryCount,
                Timestamp.from(nextRetryAt),
                lastError,
                uuidBytes(id)
        );
        assertState(id, new LeaseState(
                OutboxEventStatus.PENDING,
                retryCount,
                nextRetryAt,
                lastError,
                null,
                null
        ));
        return id;
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
                uuidBytes(id)
        );
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }

    private static byte[] uuidBytes(UUID uuid) {
        byte[] bytes = new byte[16];
        long most = uuid.getMostSignificantBits();
        long least = uuid.getLeastSignificantBits();
        for (int index = 0; index < 8; index++) {
            bytes[index] = (byte) (most >>> (8 * (7 - index)));
            bytes[index + 8] = (byte) (least >>> (8 * (7 - index)));
        }
        return bytes;
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
