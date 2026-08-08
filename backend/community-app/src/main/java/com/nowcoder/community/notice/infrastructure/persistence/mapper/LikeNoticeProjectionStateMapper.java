package com.nowcoder.community.notice.infrastructure.persistence.mapper;

import com.nowcoder.community.notice.infrastructure.persistence.dataobject.LikeNoticeProjectionStateDataObject;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Mapper
@Repository
public interface LikeNoticeProjectionStateMapper {

    int ensureSlot(
            @Param("recipientUserId") UUID recipientUserId,
            @Param("sourceRelationKey") String sourceRelationKey
    );

    LikeNoticeProjectionStateDataObject selectForUpdate(
            @Param("recipientUserId") UUID recipientUserId,
            @Param("sourceRelationKey") String sourceRelationKey
    );

    int update(LikeNoticeProjectionStateDataObject state);
}
