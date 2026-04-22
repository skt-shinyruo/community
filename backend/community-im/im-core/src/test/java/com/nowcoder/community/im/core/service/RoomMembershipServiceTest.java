package com.nowcoder.community.im.core.service;

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
class RoomMembershipServiceTest {

    @Autowired
    private RoomMembershipService roomMembershipService;

    @Test
    void joinRoom_enforcesMaxMembers() {
        UUID owner = uuid(1);
        UUID roomId = roomMembershipService.createRoom(owner, "room");

        roomMembershipService.joinRoom(uuid(2), roomId);
        roomMembershipService.joinRoom(uuid(3), roomId);

        assertThatThrownBy(() -> roomMembershipService.joinRoom(uuid(4), roomId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("room is full");
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }
}
