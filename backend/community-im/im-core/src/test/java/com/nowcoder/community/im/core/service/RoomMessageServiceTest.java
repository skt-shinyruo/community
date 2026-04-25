package com.nowcoder.community.im.core.service;

import com.nowcoder.community.im.common.command.SendRoomTextCommand;
import com.nowcoder.community.im.core.repository.RoomMessageRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RoomMessageServiceTest {

    @Autowired
    private RoomMembershipService roomMembershipService;

    @Autowired
    private RoomMessageService roomMessageService;

    @Autowired
    private RoomMessageRepository roomMessageRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void persist_isIdempotentByClientMsgId() {
        UUID sender = uuid(1);
        UUID roomId = roomMembershipService.createRoom(sender, "room");

        SendRoomTextCommand cmd = new SendRoomTextCommand(
                "req-1",
                "c1",
                sender,
                roomId,
                "hi",
                System.currentTimeMillis()
        );

        var e1 = roomMessageService.persist(cmd);
        var e2 = roomMessageService.persist(cmd);

        assertThat(e2.messageId()).isEqualTo(e1.messageId());
        assertThat(e2.seq()).isEqualTo(e1.seq());
        assertThat(e1.requestId()).isEqualTo("req-1");
        assertThat(e1.clientMsgId()).isEqualTo("c1");

        List<RoomMessageRepository.RoomMessageRow> rows =
                roomMessageRepository.listAfterSeq(roomId, 0, 100);
        assertThat(rows).hasSize(1);
    }

    @Test
    void persist_enqueuesRoomPersistedOutboxEvent() {
        UUID sender = uuid(1);
        UUID roomId = roomMembershipService.createRoom(sender, "room");

        SendRoomTextCommand cmd = new SendRoomTextCommand(
                "req-2",
                "c2",
                sender,
                roomId,
                "hi",
                System.currentTimeMillis()
        );

        roomMessageService.persist(cmd);

        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from outbox_event where event_id = ? and event_key = ?",
                Integer.class,
                "req-2:room_persisted",
                roomId.toString()
        );
        assertThat(count).isEqualTo(1);
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }
}
