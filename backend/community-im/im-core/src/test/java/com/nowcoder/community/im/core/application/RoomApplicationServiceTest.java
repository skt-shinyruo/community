package com.nowcoder.community.im.core.application;

import com.nowcoder.community.im.common.command.SendRoomTextCommand;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RoomApplicationServiceTest {

    @Autowired
    private RoomApplicationService roomApplicationService;

    @Autowired
    private RoomMessageApplicationService roomMessageApplicationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void joinRoom_enforcesMaxMembers() {
        UUID owner = uuid(1);
        UUID roomId = roomApplicationService.createRoom(owner, "room").roomId();

        roomApplicationService.joinRoom(uuid(2), roomId);
        roomApplicationService.joinRoom(uuid(3), roomId);

        assertThatThrownBy(() -> roomApplicationService.joinRoom(uuid(4), roomId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("room is full");
    }

    @Test
    void leaveRoom_removesUserRoomInboxProjection() {
        UUID owner = uuid(11);
        UUID member = uuid(12);
        UUID roomId = roomApplicationService.createRoom(owner, "room").roomId();
        roomApplicationService.joinRoom(member, roomId);

        roomMessageApplicationService.persist(new SendRoomTextCommand(
                "req-leave-room",
                "c-leave-room",
                owner,
                roomId,
                "hi",
                System.currentTimeMillis()
        ));

        assertThat(roomInboxCount(member, roomId)).isEqualTo(1);

        roomApplicationService.leaveRoom(member, roomId);

        assertThat(roomInboxCount(member, roomId)).isZero();
    }

    private int roomInboxCount(UUID userId, UUID roomId) {
        List<Integer> counts = jdbcTemplate.query(
                "select count(*) from im_user_room_inbox where user_id = ? and room_id = ?",
                (rs, rowNum) -> rs.getInt(1),
                BinaryUuidTestCodec.toBytes(userId),
                BinaryUuidTestCodec.toBytes(roomId)
        );
        return counts.isEmpty() ? 0 : counts.get(0);
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }

    private static final class BinaryUuidTestCodec {

        private BinaryUuidTestCodec() {
        }

        static byte[] toBytes(UUID uuid) {
            byte[] bytes = new byte[16];
            long most = uuid.getMostSignificantBits();
            long least = uuid.getLeastSignificantBits();
            for (int i = 0; i < 8; i++) {
                bytes[i] = (byte) (most >>> (8 * (7 - i)));
                bytes[i + 8] = (byte) (least >>> (8 * (7 - i)));
            }
            return bytes;
        }
    }
}
