package com.nowcoder.community.drive.infrastructure.persistence.mapper;

import com.nowcoder.community.drive.infrastructure.persistence.dataobject.DriveUploadDataObject;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
@Mapper
public interface DriveUploadMapper {

    DriveUploadDataObject selectById(@Param("uploadId") UUID uploadId);

    int insert(DriveUploadDataObject upload);

    int update(DriveUploadDataObject upload);
}
