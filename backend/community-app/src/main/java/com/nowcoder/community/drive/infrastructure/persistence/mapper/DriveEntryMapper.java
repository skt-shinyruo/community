package com.nowcoder.community.drive.infrastructure.persistence.mapper;

import com.nowcoder.community.drive.infrastructure.persistence.dataobject.DriveEntryDataObject;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
@Mapper
public interface DriveEntryMapper {

    DriveEntryDataObject selectById(@Param("spaceId") UUID spaceId, @Param("entryId") UUID entryId);

    DriveEntryDataObject selectActiveChildByName(@Param("spaceId") UUID spaceId,
                                                 @Param("parentId") UUID parentId,
                                                 @Param("parentKey") String parentKey,
                                                 @Param("name") String name);

    List<DriveEntryDataObject> selectActiveChildren(@Param("spaceId") UUID spaceId,
                                                    @Param("parentId") UUID parentId,
                                                    @Param("parentKey") String parentKey);

    List<DriveEntryDataObject> selectTrash(@Param("spaceId") UUID spaceId);

    List<DriveEntryDataObject> searchActive(@Param("spaceId") UUID spaceId,
                                            @Param("keyword") String keyword,
                                            @Param("limit") int limit);

    List<DriveEntryDataObject> selectDescendants(@Param("spaceId") UUID spaceId, @Param("folderId") UUID folderId);

    int insert(DriveEntryDataObject entry);

    int markDeletedIfTrashed(DriveEntryDataObject entry);

    int update(DriveEntryDataObject entry);
}
