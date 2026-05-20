package com.nowcoder.community.im.core.infrastructure.persistence.mapper;

import com.nowcoder.community.im.common.projection.RoomMembershipEntry;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
@Mapper
public interface RoomMemberMapper {

    int countMembership(@Param("roomId") UUID roomId, @Param("userId") UUID userId);

    int countMembers(@Param("roomId") UUID roomId);

    int insertMember(
            @Param("roomId") UUID roomId,
            @Param("userId") UUID userId,
            @Param("role") int role,
            @Param("version") long version
    );

    int deleteMember(@Param("roomId") UUID roomId, @Param("userId") UUID userId);

    List<UUID> selectRoomIdsByUser(
            @Param("userId") UUID userId,
            @Param("cursorRoomIdExclusive") UUID cursorRoomIdExclusive,
            @Param("limit") int limit
    );

    List<RoomMembershipEntry> scanMemberships(
            @Param("roomCursor") UUID roomCursor,
            @Param("userCursor") UUID userCursor,
            @Param("limit") int limit
    );

    Long selectCurrentMembershipProjectionVersion(@Param("id") int id);

    int insertMembershipVersionCounter(@Param("id") int id);

    Long selectMembershipVersionForUpdate(@Param("id") int id);

    int updateMembershipVersion(@Param("id") int id, @Param("currentVersion") long currentVersion);

    int insertMembershipVersionLog(
            @Param("version") long version,
            @Param("roomId") UUID roomId,
            @Param("userId") UUID userId,
            @Param("active") boolean active
    );
}
