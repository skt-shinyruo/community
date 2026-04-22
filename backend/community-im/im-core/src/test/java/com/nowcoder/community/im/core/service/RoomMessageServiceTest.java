package com.nowcoder.community.im.core.service;

import com.nowcoder.community.im.common.command.SendRoomTextCommandV1;
import com.nowcoder.community.im.core.repository.RoomMessageRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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

    @Test
    void persist_isIdempotentByClientMsgId() {
        UUID sender = uuid(1);
        UUID roomId = roomMembershipService.createRoom(sender, "room");

        SendRoomTextCommandV1 cmd = new SendRoomTextCommandV1(
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

        List<RoomMessageRepository.RoomMessageRow> rows =
                roomMessageRepository.listAfterSeq(roomId, 0, 100);
        assertThat(rows).hasSize(1);
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }
}
