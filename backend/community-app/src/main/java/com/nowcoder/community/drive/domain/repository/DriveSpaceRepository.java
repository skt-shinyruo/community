package com.nowcoder.community.drive.domain.repository;

import com.nowcoder.community.drive.domain.model.DriveSpace;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface DriveSpaceRepository {

    Optional<DriveSpace> findByUserId(UUID userId);

    Optional<DriveSpace> findById(UUID spaceId);

    boolean reserve(UUID spaceId, long bytes, Instant updatedAt);

    void save(DriveSpace space);
}
