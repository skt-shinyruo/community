package com.nowcoder.community.message.dao;

import com.nowcoder.community.message.entity.Message;
import com.nowcoder.community.message.service.dto.ConversationStats;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Mapper
@Repository
public interface MessageMapper {

    int insertMessage(Message message);

    List<Message> selectConversations(@Param("userId") int userId, @Param("offset") int offset, @Param("limit") int limit);

    List<ConversationStats> selectConversationStats(@Param("userId") int userId, @Param("conversationIds") List<String> conversationIds);

    int selectConversationCount(@Param("userId") int userId);

    List<Message> selectLetters(@Param("userId") int userId, @Param("conversationId") String conversationId, @Param("offset") int offset, @Param("limit") int limit);

    int selectLetterCount(@Param("conversationId") String conversationId);

    int selectLetterUnreadCount(@Param("userId") int userId, @Param("conversationId") String conversationId);

    List<Message> selectNotices(@Param("userId") int userId, @Param("topic") String topic, @Param("offset") int offset, @Param("limit") int limit);

    int selectNoticeCount(@Param("userId") int userId, @Param("topic") String topic);

    int selectNoticeUnreadCount(@Param("userId") int userId, @Param("topic") String topic);

    int updateLettersStatusForRecipient(@Param("ids") List<Integer> ids, @Param("status") int status, @Param("userId") int userId);

    int updateNoticesStatusForRecipient(@Param("ids") List<Integer> ids, @Param("status") int status, @Param("userId") int userId);
}
