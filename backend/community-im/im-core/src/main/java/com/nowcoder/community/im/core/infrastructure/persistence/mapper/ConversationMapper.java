package com.nowcoder.community.im.core.infrastructure.persistence.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
@Mapper
public interface ConversationMapper {

    int countByConversationId(@Param("conversationId") String conversationId);

    int insertConversation(
            @Param("conversationId") String conversationId,
            @Param("userA") UUID userA,
            @Param("userB") UUID userB
    );

    Long selectLastSeqForUpdate(@Param("conversationId") String conversationId);

    int updateLastSeq(@Param("conversationId") String conversationId, @Param("lastSeq") long lastSeq);
}
