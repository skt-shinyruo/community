package com.nowcoder.community.message.mapper;

import com.nowcoder.community.message.entity.Message;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Mapper
@Repository
public interface MessageMapper {

    int insertMessage(Message message);

    List<Message> selectNotices(@Param("userId") int userId, @Param("topic") String topic, @Param("offset") int offset, @Param("limit") int limit);

    int selectNoticeCount(@Param("userId") int userId, @Param("topic") String topic);

    int selectNoticeUnreadCount(@Param("userId") int userId, @Param("topic") String topic);

    int updateNoticesStatusForRecipient(@Param("ids") List<Integer> ids, @Param("status") int status, @Param("userId") int userId);
}
