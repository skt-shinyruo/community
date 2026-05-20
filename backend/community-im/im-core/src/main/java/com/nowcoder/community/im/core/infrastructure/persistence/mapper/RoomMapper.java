package com.nowcoder.community.im.core.infrastructure.persistence.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
@Mapper
public interface RoomMapper {

    int countByRoomId(@Param("roomId") UUID roomId);

    int insertRoom(@Param("roomId") UUID roomId, @Param("name") String name);

    Long selectLastSeqForUpdate(@Param("roomId") UUID roomId);

    int updateLastSeq(@Param("roomId") UUID roomId, @Param("lastSeq") long lastSeq);
}
