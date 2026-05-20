package com.nowcoder.community.im.core.infrastructure.persistence.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
@Mapper
public interface ConversationReadStateMapper {

    Long selectLastReadSeq(@Param("conversationId") String conversationId, @Param("userId") UUID userId);

    int updateLastReadSeqMax(
            @Param("conversationId") String conversationId,
            @Param("userId") UUID userId,
            @Param("lastReadSeq") long lastReadSeq
    );

    int insert(
            @Param("conversationId") String conversationId,
            @Param("userId") UUID userId,
            @Param("lastReadSeq") long lastReadSeq
    );
}
