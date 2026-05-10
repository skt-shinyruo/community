package com.nowcoder.community.drive.infrastructure.persistence.mapper;

import com.nowcoder.community.drive.infrastructure.persistence.dataobject.DriveSpaceDataObject;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

@Repository
@Mapper
public interface DriveSpaceMapper {

    DriveSpaceDataObject selectByUserId(@Param("userId") UUID userId);

    DriveSpaceDataObject selectById(@Param("spaceId") UUID spaceId);

    int reserve(@Param("spaceId") UUID spaceId,
                @Param("bytes") long bytes,
                @Param("updatedAt") Instant updatedAt);

    int insert(DriveSpaceDataObject space);

    int update(DriveSpaceDataObject space);
}
