package com.nowcoder.community.drive.domain.repository;

import com.nowcoder.community.drive.domain.model.DriveEntry;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DriveEntryRepository {

    Optional<DriveEntry> findById(UUID spaceId, UUID entryId);

    Optional<DriveEntry> findActiveChildByName(UUID spaceId, UUID parentId, String name);

    List<DriveEntry> listActiveChildren(UUID spaceId, UUID parentId);

    List<DriveEntry> listTrash(UUID spaceId);

    List<DriveEntry> searchActive(UUID spaceId, String keyword, int limit);

    List<UUID> listDescendantIds(UUID spaceId, UUID folderId);

    void save(DriveEntry entry);
}
