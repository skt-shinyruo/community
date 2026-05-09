package com.nowcoder.community.drive.domain.repository;

import com.nowcoder.community.drive.domain.model.DriveSpace;

import java.util.Optional;
import java.util.UUID;

public interface DriveSpaceRepository {

    Optional<DriveSpace> findByUserId(UUID userId);

    Optional<DriveSpace> findById(UUID spaceId);

    void save(DriveSpace space);
}
