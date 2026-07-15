package com.nowcoder.community.oss.infrastructure.persistence.mapper;

import com.nowcoder.community.oss.infrastructure.persistence.dataobject.OssUploadSessionDataObject;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;
import java.util.List;
import java.time.Instant;

@Repository
@Mapper
public interface OssUploadSessionMapper {

    int insert(OssUploadSessionDataObject row);

    int upsert(OssUploadSessionDataObject row);

    OssUploadSessionDataObject selectById(UUID sessionId);

    OssUploadSessionDataObject selectByRequestId(UUID requestId);

    int claimForCompletion(
            @Param("sessionId") UUID sessionId,
            @Param("updatedAt") Instant updatedAt
    );

    int recordCompletionFailure(
            @Param("sessionId") UUID sessionId,
            @Param("claimVersion") long claimVersion,
            @Param("lastError") String lastError,
            @Param("updatedAt") Instant updatedAt
    );

    int resetFailedClaim(
            @Param("sessionId") UUID sessionId,
            @Param("claimVersion") long claimVersion,
            @Param("updatedAt") Instant updatedAt,
            @Param("retryExpiresAt") Instant retryExpiresAt
    );

    int completeClaim(
            @Param("sessionId") UUID sessionId,
            @Param("claimVersion") long claimVersion,
            @Param("completedAt") Instant completedAt
    );

    int renewReadySession(
            @Param("sessionId") UUID sessionId,
            @Param("expectedExpiresAt") Instant expectedExpiresAt,
            @Param("renewedExpiresAt") Instant renewedExpiresAt,
            @Param("updatedAt") Instant updatedAt
    );

    List<OssUploadSessionDataObject> listRecoverable(
            @Param("updatedBefore") Instant updatedBefore,
            @Param("limit") int limit
    );
}
