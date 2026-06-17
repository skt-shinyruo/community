package com.nowcoder.community.notice.infrastructure.persistence.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

@Mapper
@Repository
public interface NoticeProjectionEventMapper {

    int insert(@Param("sourceEventId") String sourceEventId);
}
