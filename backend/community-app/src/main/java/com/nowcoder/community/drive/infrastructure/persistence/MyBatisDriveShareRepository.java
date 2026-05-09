package com.nowcoder.community.drive.infrastructure.persistence;

import com.nowcoder.community.drive.domain.model.DriveShare;
import com.nowcoder.community.drive.domain.repository.DriveShareRepository;
import com.nowcoder.community.drive.infrastructure.persistence.dataobject.DriveShareDataObject;
import com.nowcoder.community.drive.infrastructure.persistence.mapper.DriveShareMapper;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class MyBatisDriveShareRepository implements DriveShareRepository {

    private final DriveShareMapper mapper;

    public MyBatisDriveShareRepository(DriveShareMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<DriveShare> findById(UUID shareId) {
        return Optional.ofNullable(mapper.selectById(shareId)).map(DriveShareDataObject::toDomain);
    }

    @Override
    public Optional<DriveShare> findByToken(String shareToken) {
        return Optional.ofNullable(mapper.selectByToken(shareToken)).map(DriveShareDataObject::toDomain);
    }

    @Override
    public Optional<DriveShare> findActiveByEntryId(UUID entryId) {
        return Optional.ofNullable(mapper.selectActiveByEntryId(entryId)).map(DriveShareDataObject::toDomain);
    }

    @Override
    public void save(DriveShare share) {
        DriveShareDataObject dataObject = DriveShareDataObject.fromDomain(share);
        if (mapper.update(dataObject) == 0) {
            mapper.insert(dataObject);
        }
    }
}
