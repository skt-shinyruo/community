package com.nowcoder.community.oss.domain.repository;

import com.nowcoder.community.oss.domain.model.OssUploadSession;

import java.util.Optional;
import java.util.UUID;

public interface OssUploadSessionRepository {

    void save(OssUploadSession session);

    Optional<OssUploadSession> findById(UUID sessionId);
}
