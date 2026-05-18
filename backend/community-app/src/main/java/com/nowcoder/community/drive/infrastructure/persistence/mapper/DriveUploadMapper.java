package com.nowcoder.community.drive.infrastructure.persistence.mapper;

import com.nowcoder.community.drive.infrastructure.persistence.dataobject.DriveUploadDataObject;
import com.nowcoder.community.drive.domain.model.DriveUploadStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
@Mapper
public interface DriveUploadMapper {

    DriveUploadDataObject selectById(@Param("uploadId") UUID uploadId);

    List<DriveUploadDataObject> selectRecoverableBefore(@Param("updatedBefore") Instant updatedBefore,
                                                        @Param("limit") int limit);

    int insert(DriveUploadDataObject upload);

    int update(DriveUploadDataObject upload);

    int updateStatusIfCurrent(@Param("upload") DriveUploadDataObject upload,
                              @Param("expectedStatus") DriveUploadStatus expectedStatus);
}
