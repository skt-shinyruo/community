package com.nowcoder.community.user.dao;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

@Mapper
@Repository
public interface ConsumedEventMapper {

    @Insert("insert into user_consumed_event(event_id, consumed_at) values(#{eventId}, now())")
    int insert(@Param("eventId") String eventId);
}

