package com.nowcoder.community.drive.domain.repository;

import com.nowcoder.community.drive.domain.model.DriveSpace;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface DriveSpaceRepository {

    enum CreateStatus {
        CREATED,
        ALREADY_EXISTS,
        CONFLICT
    }

    record CreateResult(CreateStatus status, DriveSpace space) {
    }

    Optional<DriveSpace> findByUserId(UUID userId);

    Optional<DriveSpace> findById(UUID spaceId);

    DriveSpace lockById(UUID spaceId);

    boolean reserve(UUID spaceId, long bytes, Instant updatedAt);

    boolean commitReserved(UUID spaceId, long bytes, Instant updatedAt);

    boolean releaseReserved(UUID spaceId, long bytes, Instant updatedAt);

    CreateResult create(DriveSpace space);

    void save(DriveSpace space);
}
