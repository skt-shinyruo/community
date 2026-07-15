package com.nowcoder.community.oss.domain.repository;

import com.nowcoder.community.oss.domain.model.OssObjectReference;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OssObjectReferenceRepository {

    void save(OssObjectReference reference);

    OssObjectReference insertOrFindExisting(OssObjectReference reference);

    Optional<OssObjectReference> findById(UUID referenceId);

    List<OssObjectReference> findByObjectId(UUID objectId);
}
