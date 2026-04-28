package com.nowcoder.community.notice.infrastructure.persistence.mapper;

import com.nowcoder.community.notice.infrastructure.persistence.dataobject.NoticeRecordDataObject;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Mapper
@Repository
public interface NoticeMapper {

    int insertNotice(NoticeRecordDataObject notice);

    List<NoticeRecordDataObject> selectNotices(@Param("userId") UUID userId, @Param("topic") String topic, @Param("offset") int offset, @Param("limit") int limit);

    int selectNoticeCount(@Param("userId") UUID userId, @Param("topic") String topic);

    int selectNoticeUnreadCount(@Param("userId") UUID userId, @Param("topic") String topic);

    int updateNoticesStatusForRecipient(@Param("ids") List<UUID> ids, @Param("status") int status, @Param("userId") UUID userId);
}
