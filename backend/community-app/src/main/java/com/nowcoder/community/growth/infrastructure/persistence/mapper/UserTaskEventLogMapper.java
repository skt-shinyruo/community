package com.nowcoder.community.growth.infrastructure.persistence.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
@Mapper
public interface UserTaskEventLogMapper {

    int insert(
            @Param("id") UUID id,
            @Param("userId") UUID userId,
            @Param("taskCode") String taskCode,
            @Param("periodKey") String periodKey,
            @Param("sourceEventId") String sourceEventId
    );
}
