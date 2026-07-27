package com.nowcoder.community.oss.infrastructure.persistence.mapper;

import com.nowcoder.community.oss.infrastructure.persistence.dataobject.OssObjectDataObject;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
@Mapper
public interface OssObjectMapper {

    int insert(OssObjectDataObject row);

    int upsert(OssObjectDataObject row);

    OssObjectDataObject selectById(UUID objectId);

    List<UUID> selectDeletePendingIds(
            @Param("updatedBefore") Instant updatedBefore,
            @Param("limit") int limit
    );
}
