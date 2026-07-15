package com.nowcoder.community.drive.domain.repository;

import com.nowcoder.community.drive.domain.model.DriveEntry;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DriveEntryRepository {

    enum CreateStatus {
        CREATED,
        ALREADY_EXISTS,
        ACTIVE_NAME_CONFLICT,
        CONFLICT
    }

    record CreateResult(CreateStatus status, DriveEntry entry) {
    }

    Optional<DriveEntry> findById(UUID spaceId, UUID entryId);

    Optional<DriveEntry> findActiveChildByName(UUID spaceId, UUID parentId, String name);

    List<DriveEntry> listActiveChildren(UUID spaceId, UUID parentId);

    List<DriveEntry> listTrash(UUID spaceId);

    List<DriveEntry> searchActive(UUID spaceId, String keyword, int limit);

    List<UUID> listDescendantIds(UUID spaceId, UUID folderId);

    CreateResult create(DriveEntry entry);

    void save(DriveEntry entry);
}
