package com.nowcoder.community.oss.domain.repository;

import com.nowcoder.community.oss.domain.model.OssObject;

import java.util.Optional;
import java.util.UUID;

public interface OssObjectRepository {

    void save(OssObject object);

    Optional<OssObject> findById(UUID objectId);
}
