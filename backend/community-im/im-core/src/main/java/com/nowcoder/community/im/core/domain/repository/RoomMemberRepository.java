package com.nowcoder.community.im.core.domain.repository;

import com.nowcoder.community.im.common.projection.RoomMembershipEntry;

import java.util.List;
import java.util.UUID;

public interface RoomMemberRepository {

    boolean isMember(UUID roomId, UUID userId);

    int countMembers(UUID roomId);

    void addMember(UUID roomId, UUID userId, int role);

    void removeMember(UUID roomId, UUID userId);

    List<UUID> listRoomIdsByUser(UUID userId, UUID cursorRoomIdExclusive, int limit);

    List<RoomMembershipEntry> scanMemberships(UUID afterRoomId, UUID afterUserId, int limit);
}
