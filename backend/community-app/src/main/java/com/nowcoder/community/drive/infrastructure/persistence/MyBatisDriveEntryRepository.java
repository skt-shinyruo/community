package com.nowcoder.community.drive.infrastructure.persistence;

import com.nowcoder.community.drive.domain.model.DriveEntry;
import com.nowcoder.community.drive.domain.repository.DriveEntryRepository;
import com.nowcoder.community.drive.infrastructure.persistence.dataobject.DriveEntryDataObject;
import com.nowcoder.community.drive.infrastructure.persistence.mapper.DriveEntryMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
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
