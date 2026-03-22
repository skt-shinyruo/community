package com.nowcoder.community.growth.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

@Repository
@Mapper
public interface UserTaskEventLogMapper {

    int insert(
            @Param("userId") int userId,
            @Param("taskCode") String taskCode,
            @Param("periodKey") String periodKey,
            @Param("sourceEventId") String sourceEventId
    );
}
