package com.nowcoder.community.im.core.infrastructure.persistence.mapper;

import com.nowcoder.community.im.core.infrastructure.persistence.dataobject.PrivateMessageDataObject;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
@Mapper
public interface PrivateMessageMapper {

    List<PrivateMessageDataObject> selectByIdempotency(
            @Param("conversationId") String conversationId,
            @Param("fromUserId") UUID fromUserId,
            @Param("clientMsgId") String clientMsgId
    );

    int insert(PrivateMessageDataObject row);

    List<PrivateMessageDataObject> selectAfterSeq(
            @Param("conversationId") String conversationId,
            @Param("afterSeqExclusive") long afterSeqExclusive,
            @Param("limit") int limit
    );

    List<PrivateMessageDataObject> selectBeforeSeq(
            @Param("conversationId") String conversationId,
            @Param("beforeSeqExclusive") Long beforeSeqExclusive,
            @Param("limit") int limit
    );
}
