package com.nowcoder.community.im.core.infrastructure.persistence.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
@Mapper
public interface RoomReadStateMapper {

    Long selectLastReadSeq(@Param("roomId") UUID roomId, @Param("userId") UUID userId);

    int updateLastReadSeqMax(
            @Param("roomId") UUID roomId,
            @Param("userId") UUID userId,
            @Param("lastReadSeq") long lastReadSeq
    );

    int insert(
            @Param("roomId") UUID roomId,
            @Param("userId") UUID userId,
            @Param("lastReadSeq") long lastReadSeq
    );
}
