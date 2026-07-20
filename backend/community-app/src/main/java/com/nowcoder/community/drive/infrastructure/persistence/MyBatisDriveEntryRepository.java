package com.nowcoder.community.drive.infrastructure.persistence;

import com.nowcoder.community.drive.domain.model.DriveEntry;
import com.nowcoder.community.drive.domain.repository.DriveEntryRepository;
import com.nowcoder.community.drive.infrastructure.persistence.dataobject.DriveEntryDataObject;
import com.nowcoder.community.drive.infrastructure.persistence.mapper.DriveEntryMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Repository
public class MyBatisDriveEntryRepository implements DriveEntryRepository {

    private final DriveEntryMapper mapper;

    public MyBatisDriveEntryRepository(DriveEntryMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<DriveEntry> findById(UUID spaceId, UUID entryId) {
        return Optional.ofNullable(mapper.selectById(spaceId, entryId)).map(DriveEntryDataObject::toDomain);
    }

    @Override
    public Optional<DriveEntry> findActiveChildByName(UUID spaceId, UUID parentId, String name) {
        return Optional.ofNullable(mapper.selectActiveChildByName(spaceId, parentId, DriveEntryDataObject.parentKey(parentId), name))
                .map(DriveEntryDataObject::toDomain);
    }

    @Override
    public List<DriveEntry> listActiveChildren(UUID spaceId, UUID parentId) {
        return toDomainList(mapper.selectActiveChildren(spaceId, parentId, DriveEntryDataObject.parentKey(parentId)));
    }

    @Override
    public List<DriveEntry> listTrash(UUID spaceId) {
        return toDomainList(mapper.selectTrash(spaceId));
    }

    @Override
    public List<DriveEntry> searchActive(UUID spaceId, String keyword, int limit) {
        return toDomainList(mapper.searchActive(spaceId, keyword, limit));
    }

    @Override
    public List<UUID> listDescendantIds(UUID spaceId, UUID folderId) {
        return mapper.selectDescendants(spaceId, folderId).stream()
                .map(DriveEntryDataObject::getEntryId)
                .toList();
    }

    @Override
    public CreateResult create(DriveEntry entry) {
        try {
            return mapper.insert(DriveEntryDataObject.fromDomain(entry)) == 1
                    ? new CreateResult(CreateStatus.CREATED, entry)
                    : new CreateResult(CreateStatus.CONFLICT, null);
        } catch (DuplicateKeyException ignored) {
            DriveEntry existingById = findById(entry.spaceId(), entry.entryId()).orElse(null);
            if (existingById != null) {
                return sameCreationFingerprint(existingById, entry)
                        ? new CreateResult(CreateStatus.ALREADY_EXISTS, existingById)
                        : new CreateResult(CreateStatus.CONFLICT, existingById);
            }
            DriveEntry existingByName = findActiveChildByName(entry.spaceId(), entry.parentId(), entry.name())
                    .orElse(null);
            if (existingByName != null) {
                return new CreateResult(CreateStatus.ACTIVE_NAME_CONFLICT, existingByName);
            }
            return new CreateResult(CreateStatus.CONFLICT, null);
        }
    }

    private static boolean sameCreationFingerprint(DriveEntry existing, DriveEntry candidate) {
        return Objects.equals(existing.entryId(), candidate.entryId())
                && Objects.equals(existing.spaceId(), candidate.spaceId())
                && Objects.equals(existing.parentId(), candidate.parentId())
                && existing.type() == candidate.type()
                && existing.status() == candidate.status()
                && Objects.equals(existing.name(), candidate.name())
                && Objects.equals(existing.objectId(), candidate.objectId())
                && Objects.equals(existing.versionId(), candidate.versionId())
                && existing.sizeBytes() == candidate.sizeBytes()
                && Objects.equals(existing.mimeType(), candidate.mimeType())
                && Objects.equals(existing.trashedAt(), candidate.trashedAt())
                && Objects.equals(existing.deleteAfter(), candidate.deleteAfter())
                && Objects.equals(existing.trashRootId(), candidate.trashRootId());
    }

    @Override
    public boolean markDeletedIfTrashed(DriveEntry deletedEntry) {
        return mapper.markDeletedIfTrashed(DriveEntryDataObject.fromDomain(deletedEntry)) == 1;
    }

    @Override
    public void save(DriveEntry entry) {
        DriveEntryDataObject dataObject = DriveEntryDataObject.fromDomain(entry);
        if (mapper.update(dataObject) == 0) {
            mapper.insert(dataObject);
        }
    }

    private static List<DriveEntry> toDomainList(List<DriveEntryDataObject> dataObjects) {
        if (dataObjects == null || dataObjects.isEmpty()) {
            return List.of();
        }
        return dataObjects.stream().map(DriveEntryDataObject::toDomain).toList();
    }
}
