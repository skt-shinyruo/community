package com.nowcoder.community.drive.domain.repository;

import com.nowcoder.community.drive.domain.model.DriveShare;

import java.util.Optional;
import java.util.UUID;

public interface DriveShareRepository {

    Optional<DriveShare> findById(UUID shareId);

    Optional<DriveShare> findByToken(String shareToken);

    Optional<DriveShare> findActiveByEntryId(UUID entryId);

    void save(DriveShare share);
}
