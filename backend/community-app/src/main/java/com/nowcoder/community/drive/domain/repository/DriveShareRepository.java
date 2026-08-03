package com.nowcoder.community.drive.domain.repository;

import com.nowcoder.community.drive.domain.model.DriveShare;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

public interface DriveShareRepository {

    Optional<DriveShare> findById(UUID shareId);

    Optional<DriveShare> findByToken(String shareToken);

    Optional<DriveShare> findActiveByEntryId(UUID entryId);

    List<DriveShare> findByCreatedBy(UUID createdBy, int offset, int limit);

    void save(DriveShare share);
}
