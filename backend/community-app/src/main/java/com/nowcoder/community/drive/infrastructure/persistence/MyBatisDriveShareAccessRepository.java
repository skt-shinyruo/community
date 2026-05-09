package com.nowcoder.community.drive.infrastructure.persistence;

import com.nowcoder.community.drive.domain.repository.DriveShareAccessRepository;
import com.nowcoder.community.drive.infrastructure.persistence.dataobject.DriveShareAccessDataObject;
import com.nowcoder.community.drive.infrastructure.persistence.mapper.DriveShareAccessMapper;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

@Repository
public class MyBatisDriveShareAccessRepository implements DriveShareAccessRepository {

    private final DriveShareAccessMapper mapper;

    public MyBatisDriveShareAccessRepository(DriveShareAccessMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void record(UUID accessId, UUID shareId, String visitorFingerprint, boolean success, Instant accessedAt) {
        mapper.insert(new DriveShareAccessDataObject(accessId, shareId, visitorFingerprint, success, accessedAt));
    }
}
