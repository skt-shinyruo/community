package com.nowcoder.community.oss.domain.repository;

import com.nowcoder.community.oss.domain.model.OssAccessGrant;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OssAccessGrantRepository {

    void save(OssAccessGrant grant);

    Optional<OssAccessGrant> findById(UUID grantId);

    List<OssAccessGrant> findByObjectId(UUID objectId);
}
