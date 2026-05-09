package com.nowcoder.community.drive.domain.repository;

import java.time.Instant;
import java.util.UUID;

public interface DriveShareAccessRepository {

    void record(UUID accessId, UUID shareId, String visitorFingerprint, boolean success, Instant accessedAt);
}
