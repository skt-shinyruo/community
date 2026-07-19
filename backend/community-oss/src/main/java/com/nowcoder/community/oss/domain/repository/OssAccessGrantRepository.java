package com.nowcoder.community.oss.domain.repository;

import com.nowcoder.community.oss.domain.model.OssAccessGrant;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public interface OssAccessGrantRepository {

    void save(OssAccessGrant grant);

    Optional<OssAccessGrant> findById(UUID grantId);

    List<OssAccessGrant> findByObjectId(UUID objectId);

    default List<OssAccessGrant> findReadGrants(UUID objectId, UUID versionId, String principalValue) {
        Objects.requireNonNull(objectId, "objectId");
        if (principalValue == null || principalValue.isBlank()) {
            return List.of();
        }
        String normalizedPrincipal = principalValue.trim();
        return findByObjectId(objectId).stream()
                .filter(grant -> objectId.equals(grant.objectId()))
                .filter(grant -> grant.versionId() == null || grant.versionId().equals(versionId))
                .filter(grant -> "USER".equals(grant.principalType()))
                .filter(grant -> normalizedPrincipal.equals(grant.principalValue()))
                .filter(grant -> "READ".equals(grant.permission()))
                .toList();
    }
}
