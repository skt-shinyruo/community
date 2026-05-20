package com.nowcoder.community.im.core.infrastructure.persistence.mapper;

import com.nowcoder.community.im.core.infrastructure.persistence.dataobject.RoomMessageDataObject;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
@Mapper
public interface RoomMessageMapper {

    List<RoomMessageDataObject> selectByIdempotency(
            @Param("roomId") UUID roomId,
            @Param("fromUserId") UUID fromUserId,
            @Param("clientMsgId") String clientMsgId
    );

    int insert(RoomMessageDataObject row);

    List<RoomMessageDataObject> selectAfterSeq(
            @Param("roomId") UUID roomId,
            @Param("afterSeqExclusive") long afterSeqExclusive,
            @Param("limit") int limit
    );
}
