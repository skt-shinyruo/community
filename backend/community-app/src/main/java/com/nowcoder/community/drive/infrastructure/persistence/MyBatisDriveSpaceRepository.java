package com.nowcoder.community.drive.infrastructure.persistence;

import com.nowcoder.community.drive.domain.model.DriveSpace;
import com.nowcoder.community.drive.domain.repository.DriveSpaceRepository;
import com.nowcoder.community.drive.infrastructure.persistence.dataobject.DriveSpaceDataObject;
import com.nowcoder.community.drive.infrastructure.persistence.mapper.DriveSpaceMapper;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public class MyBatisDriveSpaceRepository implements DriveSpaceRepository {

    private final DriveSpaceMapper mapper;

    public MyBatisDriveSpaceRepository(DriveSpaceMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<DriveSpace> findByUserId(UUID userId) {
        return Optional.ofNullable(mapper.selectByUserId(userId)).map(DriveSpaceDataObject::toDomain);
    }

    @Override
    public Optional<DriveSpace> findById(UUID spaceId) {
        return Optional.ofNullable(mapper.selectById(spaceId)).map(DriveSpaceDataObject::toDomain);
    }

    @Override
    public DriveSpace lockById(UUID spaceId) {
        return Optional.ofNullable(mapper.selectByIdForUpdate(spaceId))
                .map(DriveSpaceDataObject::toDomain)
                .orElse(null);
    }

    @Override
    public boolean reserve(UUID spaceId, long bytes, Instant updatedAt) {
        return mapper.reserve(spaceId, bytes, updatedAt) == 1;
    }

    @Override
    public boolean commitReserved(UUID spaceId, long bytes, Instant updatedAt) {
        return mapper.commitReserved(spaceId, bytes, updatedAt) == 1;
    }

    @Override
    public boolean releaseReserved(UUID spaceId, long bytes, Instant updatedAt) {
        return mapper.releaseReserved(spaceId, bytes, updatedAt) == 1;
    }

    @Override
    public void save(DriveSpace space) {
        DriveSpaceDataObject dataObject = DriveSpaceDataObject.fromDomain(space);
        if (mapper.update(dataObject) == 0) {
            mapper.insert(dataObject);
        }
    }
}
