package com.nowcoder.community.oss.domain.repository;

import com.nowcoder.community.oss.domain.model.OssObjectVersion;

import java.util.Optional;
import java.util.UUID;

public interface OssObjectVersionRepository {

    void save(OssObjectVersion version);

    Optional<OssObjectVersion> findById(UUID versionId);
}
