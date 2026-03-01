package com.nowcoder.community.message.dao;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.springframework.stereotype.Repository;

@Mapper
@Repository
public interface ConsumedEventMapper {

    /**
     * 仅用于排查/测试：生产消费逻辑应使用 insert-first（以唯一约束为准），避免 count->insert 的竞态窗口。
     */
    @Deprecated
    @Select("select count(1) from consumed_event where event_id = #{eventId}")
    int countByEventId(@Param("eventId") String eventId);

    @Insert("insert into consumed_event(event_id, consumed_at) values(#{eventId}, now())")
    int insert(@Param("eventId") String eventId);
}
