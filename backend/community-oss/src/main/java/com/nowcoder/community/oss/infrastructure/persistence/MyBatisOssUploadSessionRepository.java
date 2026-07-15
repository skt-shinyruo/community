package com.nowcoder.community.oss.infrastructure.persistence;

import com.nowcoder.community.oss.domain.model.OssUploadSession;
import com.nowcoder.community.oss.domain.repository.OssUploadSessionRepository;
import com.nowcoder.community.oss.infrastructure.persistence.dataobject.OssUploadSessionDataObject;
import com.nowcoder.community.oss.infrastructure.persistence.mapper.OssUploadSessionMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;
import java.time.Instant;
import java.util.UUID;

@Repository
public class MyBatisOssUploadSessionRepository implements OssUploadSessionRepository {

    private final OssUploadSessionMapper mapper;

    public MyBatisOssUploadSessionRepository(OssUploadSessionMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public boolean create(OssUploadSession session) {
        try {
            return mapper.insert(OssUploadSessionDataObject.from(session)) == 1;
        } catch (DuplicateKeyException duplicate) {
            return false;
        }
    }

    @Override
    public void save(OssUploadSession session) {
        mapper.upsert(OssUploadSessionDataObject.from(session));
    }

    @Override
    public Optional<OssUploadSession> findById(UUID sessionId) {
        return Optional.ofNullable(mapper.selectById(sessionId)).map(OssUploadSessionDataObject::toDomain);
    }

    @Override
    public Optional<OssUploadSession> findByRequestId(UUID requestId) {
        return Optional.ofNullable(mapper.selectByRequestId(requestId)).map(OssUploadSessionDataObject::toDomain);
    }

    @Override
    public boolean claimForCompletion(UUID sessionId, Instant updatedAt) {
        return mapper.claimForCompletion(sessionId, updatedAt) == 1;
    }

    @Override
    public boolean recordCompletionFailure(
            UUID sessionId,
            long claimVersion,
            String lastError,
            Instant updatedAt
    ) {
        return mapper.recordCompletionFailure(sessionId, claimVersion, lastError, updatedAt) == 1;
    }

    @Override
    public boolean resetFailedClaim(
            UUID sessionId,
            long claimVersion,
            Instant updatedAt,
            Instant retryExpiresAt
    ) {
        return mapper.resetFailedClaim(sessionId, claimVersion, updatedAt, retryExpiresAt) == 1;
    }

    @Override
    public boolean completeClaim(UUID sessionId, long claimVersion, Instant completedAt) {
        return mapper.completeClaim(sessionId, claimVersion, completedAt) == 1;
    }

    @Override
    public boolean renewReadySession(
            UUID sessionId,
            Instant expectedExpiresAt,
            Instant renewedExpiresAt,
            Instant updatedAt
    ) {
        return mapper.renewReadySession(
                sessionId, expectedExpiresAt, renewedExpiresAt, updatedAt) == 1;
    }

    @Override
    public List<OssUploadSession> listRecoverable(Instant updatedBefore, int limit) {
        int safeLimit = Math.max(1, Math.min(500, limit));
        return mapper.listRecoverable(updatedBefore, safeLimit).stream()
                .map(OssUploadSessionDataObject::toDomain)
                .toList();
    }
}
