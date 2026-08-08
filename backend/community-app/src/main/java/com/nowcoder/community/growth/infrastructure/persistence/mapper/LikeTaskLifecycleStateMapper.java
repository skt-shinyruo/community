package com.nowcoder.community.growth.infrastructure.persistence.mapper;

import com.nowcoder.community.growth.infrastructure.persistence.dataobject.LikeTaskLifecycleStateDataObject;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Mapper
@Repository
public interface LikeTaskLifecycleStateMapper {

    int ensureSlot(
            @Param("recipientUserId") UUID recipientUserId,
            @Param("relationKey") String relationKey
    );

    LikeTaskLifecycleStateDataObject selectForUpdate(
            @Param("recipientUserId") UUID recipientUserId,
            @Param("relationKey") String relationKey
    );

    int update(LikeTaskLifecycleStateDataObject state);
}
