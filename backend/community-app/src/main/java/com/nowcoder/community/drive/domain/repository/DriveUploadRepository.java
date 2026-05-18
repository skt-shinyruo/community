package com.nowcoder.community.drive.domain.repository;

import com.nowcoder.community.drive.domain.model.DriveUpload;
import com.nowcoder.community.drive.domain.model.DriveUploadStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DriveUploadRepository {

    Optional<DriveUpload> findById(UUID uploadId);

    boolean transitionStatus(DriveUpload upload, DriveUploadStatus expectedStatus);

    List<DriveUpload> listRecoverableBefore(Instant updatedBefore, int limit);

    void save(DriveUpload upload);
}
