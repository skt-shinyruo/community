package com.nowcoder.community.user.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
@Mapper
public interface UserScoreLogMapper {

    int insert(
            @Param("id") UUID id,
            @Param("userId") UUID userId,
            @Param("eventId") String eventId,
            @Param("eventType") String eventType,
            @Param("delta") int delta
    );
}
