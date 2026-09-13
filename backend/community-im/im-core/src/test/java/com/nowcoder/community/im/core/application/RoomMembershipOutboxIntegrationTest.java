package com.nowcoder.community.im.core.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.common.id.BinaryUuidCodec;
import com.nowcoder.community.common.outbox.JdbcOutboxEventStore;
import com.nowcoder.community.im.core.infrastructure.persistence.MyBatisUserInboxRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.nowcoder.community.im.core.support.ImCoreTestDatabaseCleaner.cleanAll;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "im.room-member-change.publisher=kafka")
class RoomMembershipOutboxIntegrationTest {

    private static final String MEMBER_CHANGED_TOPIC = "im.event.room-member-changed";

    @Autowired
    private RoomApplicationService roomApplicationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoSpyBean
    private MyBatisUserInboxRepository userInboxPersistence;

    @MockitoSpyBean
    private JdbcOutboxEventStore outboxEventStore;

    @BeforeEach
    void setUp() {
        cleanAll(jdbcTemplate);
    }

    @AfterEach
    void tearDown() {
        cleanAll(jdbcTemplate);
    }

    @Test
    void committedMembershipChanges_enqueueOutboxRowsCarryingMembershipVersions() throws Exception {
        UUID owner = uuid(1);
        UUID member = uuid(2);

        UUID roomId = roomApplicationService.createRoom(owner, "room").roomId();
        roomApplicationService.joinRoom(member, roomId);
        roomApplicationService.leaveRoom(member, roomId);

        List<JsonNode> events = memberChangedEvents();
        assertThat(events).hasSize(3);
        Map<Long, JsonNode> byVersion = events.stream()
                .collect(Collectors.toMap(e -> e.path("version").asLong(), Function.identity()));
        assertThat(byVersion.keySet()).containsExactlyInAnyOrder(1L, 2L, 3L);

        JsonNode ownerJoined = byVersion.get(1L);
        assertThat(ownerJoined.path("userId").asText()).isEqualTo(owner.toString());
        assertThat(ownerJoined.path("action").asText()).isEqualTo("JOINED");

        JsonNode memberJoined = byVersion.get(2L);
        assertThat(memberJoined.path("userId").asText()).isEqualTo(member.toString());
        assertThat(memberJoined.path("action").asText()).isEqualTo("JOINED");

        JsonNode memberLeft = byVersion.get(3L);
        assertThat(memberLeft.path("userId").asText()).isEqualTo(member.toString());
        assertThat(memberLeft.path("action").asText()).isEqualTo("LEFT");

        for (JsonNode event : events) {
            assertThat(event.path("roomId").asText()).isEqualTo(roomId.toString());
            assertThat(event.path("eventId").asText()).isNotBlank();
        }

        assertThat(membershipLogCount(1L, roomId, owner, 1)).isEqualTo(1);
        assertThat(membershipLogCount(2L, roomId, member, 1)).isEqualTo(1);
        assertThat(membershipLogCount(3L, roomId, member, 0)).isEqualTo(1);

        assertThat(memberChangedRows("status")).containsOnly("PENDING");
        assertThat(memberChangedRows("event_key")).containsOnly(roomId.toString());
    }

    @Test
    void inboxWriteFailure_rollsBackMembershipChangeAndOutboxRow() {
        UUID owner = uuid(11);
        UUID member = uuid(12);
        UUID roomId = roomApplicationService.createRoom(owner, "room").roomId();
        int committedEvents = memberChangedEventCount();

        doThrow(new IllegalStateException("inbox write failed"))
                .when(userInboxPersistence)
                .ensureRoomMemberInbox(any(UUID.class), any(UUID.class));

        assertThatThrownBy(() -> roomApplicationService.joinRoom(member, roomId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("inbox write failed");

        assertThat(membershipCount(roomId, member)).isZero();
        assertThat(roomInboxCount(member, roomId)).isZero();
        assertThat(memberChangedEventCount()).isEqualTo(committedEvents);
    }

    @Test
    void outboxWriteFailure_rollsBackMembershipChangeAndInbox() throws Exception {
        UUID owner = uuid(21);
        UUID member = uuid(22);
        UUID roomId = roomApplicationService.createRoom(owner, "room").roomId();

        doThrow(new IllegalStateException("outbox insert failed"))
                .when(outboxEventStore)
                .enqueue(anyString(), eq(MEMBER_CHANGED_TOPIC), anyString(), anyString());

        assertThatThrownBy(() -> roomApplicationService.joinRoom(member, roomId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("outbox insert failed");

        assertThat(membershipCount(roomId, member)).isZero();
        assertThat(roomInboxCount(member, roomId)).isZero();
        assertThat(memberChangedEvents())
                .noneMatch(event -> member.toString().equals(event.path("userId").asText("")));
    }

    @Test
    void repeatedJoinAndLeaveWithoutStateChange_enqueueNoNewEvents() {
        UUID owner = uuid(31);
        UUID member = uuid(32);
        UUID roomId = roomApplicationService.createRoom(owner, "room").roomId();
        roomApplicationService.joinRoom(member, roomId);
        int afterJoin = memberChangedEventCount();

        roomApplicationService.joinRoom(member, roomId);
        assertThat(memberChangedEventCount()).isEqualTo(afterJoin);

        roomApplicationService.leaveRoom(member, roomId);
        int afterLeave = memberChangedEventCount();
        assertThat(afterLeave).isEqualTo(afterJoin + 1);

        roomApplicationService.leaveRoom(member, roomId);
        assertThat(memberChangedEventCount()).isEqualTo(afterLeave);
    }

    private List<JsonNode> memberChangedEvents() throws Exception {
        List<String> payloads = memberChangedRows("payload");
        List<JsonNode> events = new ArrayList<>(payloads.size());
        for (String payload : payloads) {
            events.add(objectMapper.readTree(payload));
        }
        return events;
    }

    private List<String> memberChangedRows(String column) {
        return jdbcTemplate.query(
                "select " + column + " from outbox_event where topic = ? order by id",
                (rs, rowNum) -> rs.getString(1),
                MEMBER_CHANGED_TOPIC
        );
    }

    private int memberChangedEventCount() {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from outbox_event where topic = ?",
                Integer.class,
                MEMBER_CHANGED_TOPIC
        );
        return count == null ? 0 : count;
    }

    private int membershipCount(UUID roomId, UUID userId) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from im_room_member where room_id = ? and user_id = ?",
                Integer.class,
                BinaryUuidCodec.toBytes(roomId),
                BinaryUuidCodec.toBytes(userId)
        );
        return count == null ? 0 : count;
    }

    private int membershipLogCount(long version, UUID roomId, UUID userId, int active) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from im_membership_version_log "
                        + "where version = ? and room_id = ? and user_id = ? and active = ?",
                Integer.class,
                version,
                BinaryUuidCodec.toBytes(roomId),
                BinaryUuidCodec.toBytes(userId),
                active
        );
        return count == null ? 0 : count;
    }

    private int roomInboxCount(UUID userId, UUID roomId) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from im_user_room_inbox where user_id = ? and room_id = ?",
                Integer.class,
                BinaryUuidCodec.toBytes(userId),
                BinaryUuidCodec.toBytes(roomId)
        );
        return count == null ? 0 : count;
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }
}
