package com.nowcoder.community.im.core.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RoomMembershipServiceTest {

    @Autowired
    private RoomMembershipService roomMembershipService;

    @Test
    void joinRoom_enforcesMaxMembers() {
        int owner = 1;
        long roomId = roomMembershipService.createRoom(owner, "room");

        roomMembershipService.joinRoom(2, roomId);
        roomMembershipService.joinRoom(3, roomId);

        assertThatThrownBy(() -> roomMembershipService.joinRoom(4, roomId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("room is full");
    }
}

