package com.nowcoder.community.social.outbox;

// Outbox 事件 Mapper：负责事件入库、批量认领与状态更新。
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

@Mapper
public interface OutboxEventMapper {

    int insert(OutboxEvent event);

    List<Long> selectCandidateIds(@Param("now") Date now, @Param("limit") int limit);

    int markSending(@Param("ids") List<Long> ids, @Param("now") Date now);

    List<OutboxEvent> selectByIds(@Param("ids") List<Long> ids);

    int markSent(@Param("id") long id, @Param("now") Date now);

    int markRetry(
            @Param("id") long id,
            @Param("retryCount") int retryCount,
            @Param("nextRetryAt") Date nextRetryAt,
            @Param("lastError") String lastError,
            @Param("now") Date now
    );

    int markFailed(@Param("id") long id, @Param("lastError") String lastError, @Param("now") Date now);

    int countByStatus(@Param("status") String status);

    List<Long> selectFailedIds(@Param("limit") int limit);

    int markRetryByIds(@Param("ids") List<Long> ids, @Param("now") Date now);
}

