package com.nowcoder.community.drive.infrastructure.persistence.mapper;

import com.nowcoder.community.drive.infrastructure.persistence.dataobject.DriveShareDataObject;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;
import java.util.List;

@Repository
@Mapper
public interface DriveShareMapper {

    DriveShareDataObject selectById(@Param("shareId") UUID shareId);

    DriveShareDataObject selectByToken(@Param("shareToken") String shareToken);

    DriveShareDataObject selectActiveByEntryId(@Param("entryId") UUID entryId);

    List<DriveShareDataObject> selectByCreatedBy(@Param("createdBy") UUID createdBy,
                                                @Param("offset") int offset,
                                                @Param("limit") int limit);

    int insert(DriveShareDataObject share);

    int update(DriveShareDataObject share);
}
