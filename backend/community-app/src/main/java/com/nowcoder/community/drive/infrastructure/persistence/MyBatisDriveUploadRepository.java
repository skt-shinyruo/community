package com.nowcoder.community.drive.infrastructure.persistence;

import com.nowcoder.community.drive.domain.model.DriveUpload;
import com.nowcoder.community.drive.domain.model.DriveUploadStatus;
import com.nowcoder.community.drive.domain.repository.DriveUploadRepository;
import com.nowcoder.community.drive.infrastructure.persistence.dataobject.DriveUploadDataObject;
import com.nowcoder.community.drive.infrastructure.persistence.mapper.DriveUploadMapper;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class MyBatisDriveUploadRepository implements DriveUploadRepository {

    private final DriveUploadMapper mapper;

    public MyBatisDriveUploadRepository(DriveUploadMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<DriveUpload> findById(UUID uploadId) {
        return Optional.ofNullable(mapper.selectById(uploadId)).map(DriveUploadDataObject::toDomain);
    }

    @Override
    public boolean transitionStatus(DriveUpload upload, DriveUploadStatus expectedStatus) {
        if (upload == null || expectedStatus == null) {
            return false;
        }
        return mapper.updateStatusIfCurrent(DriveUploadDataObject.fromDomain(upload), expectedStatus) == 1;
    }

    @Override
    public List<DriveUpload> listRecoverableBefore(Instant updatedBefore, int limit) {
        if (updatedBefore == null || limit <= 0) {
            return List.of();
        }
        return mapper.selectRecoverableBefore(updatedBefore, limit).stream()
                .map(DriveUploadDataObject::toDomain)
                .toList();
    }

    @Override
    public void save(DriveUpload upload) {
        DriveUploadDataObject dataObject = DriveUploadDataObject.fromDomain(upload);
        if (mapper.update(dataObject) == 0) {
            mapper.insert(dataObject);
        }
    }
}
