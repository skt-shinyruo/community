package com.nowcoder.community.im.core.application;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RoomApplicationServiceTest {

    @Autowired
    private RoomApplicationService roomApplicationService;

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

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }
}
