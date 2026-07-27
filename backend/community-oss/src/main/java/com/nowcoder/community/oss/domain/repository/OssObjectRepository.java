package com.nowcoder.community.oss.domain.repository;

import com.nowcoder.community.oss.domain.model.OssObject;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OssObjectRepository {

    default boolean create(OssObject object) {
        save(object);
        return true;
    }

    void save(OssObject object);

    Optional<OssObject> findById(UUID objectId);

    default List<UUID> listDeletePendingIds(Instant updatedBefore, int limit) {
        return List.of();
    }
}
