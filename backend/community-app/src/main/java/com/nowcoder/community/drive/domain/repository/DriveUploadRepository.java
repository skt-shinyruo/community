package com.nowcoder.community.drive.domain.repository;

import com.nowcoder.community.drive.domain.model.DriveUpload;

import java.util.Optional;
import java.util.UUID;

public interface DriveUploadRepository {

    Optional<DriveUpload> findById(UUID uploadId);

    void save(DriveUpload upload);
}
