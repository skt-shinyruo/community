package com.nowcoder.community.drive.infrastructure.persistence;

import com.nowcoder.community.drive.domain.model.DriveSpace;
import com.nowcoder.community.drive.domain.repository.DriveSpaceRepository;
import com.nowcoder.community.drive.infrastructure.persistence.dataobject.DriveSpaceDataObject;
import com.nowcoder.community.drive.infrastructure.persistence.mapper.DriveSpaceMapper;
import org.springframework.stereotype.Repository;

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
    public void save(DriveSpace space) {
        DriveSpaceDataObject dataObject = DriveSpaceDataObject.fromDomain(space);
        if (mapper.update(dataObject) == 0) {
            mapper.insert(dataObject);
        }
    }
}
