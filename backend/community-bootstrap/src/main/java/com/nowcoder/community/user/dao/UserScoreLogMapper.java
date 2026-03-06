package com.nowcoder.community.user.dao;

import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Repository;

@Repository
@Mapper
public interface UserScoreLogMapper {

    int insert(int userId, String eventId, String eventType, int delta);
}

