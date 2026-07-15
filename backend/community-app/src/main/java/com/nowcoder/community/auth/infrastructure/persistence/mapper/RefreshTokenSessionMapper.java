package com.nowcoder.community.auth.infrastructure.persistence.mapper;

import com.nowcoder.community.auth.infrastructure.persistence.dataobject.RefreshTokenSessionDataObject;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

@Repository
@Mapper
public interface RefreshTokenSessionMapper {

    int storeIfFamilyActive(
            @Param("tokenHash") String tokenHash,
            @Param("userId") UUID userId,
            @Param("familyId") String familyId,
            @Param("securityVersionAtIssue") long securityVersionAtIssue,
            @Param("expiresAt") Instant expiresAt
    );

    RefreshTokenSessionDataObject selectByTokenHash(@Param("tokenHash") String tokenHash);

    int consumeActive(@Param("tokenHash") String tokenHash, @Param("now") Instant now);

    int recoverExpiredPending(@Param("tokenHash") String tokenHash, @Param("now") Instant now);

    int beginRotation(
            @Param("tokenHash") String tokenHash,
            @Param("pendingExpiresAt") Instant pendingExpiresAt,
            @Param("now") Instant now
    );

    int finishPendingRotation(
            @Param("tokenHash") String tokenHash,
            @Param("userId") UUID userId,
            @Param("familyId") String familyId,
            @Param("securityVersionAtIssue") long securityVersionAtIssue,
            @Param("now") Instant now
    );

    int rollbackPendingRotation(@Param("tokenHash") String tokenHash);

    int revoke(@Param("tokenHash") String tokenHash);

    int upsertFamilyRevocation(@Param("familyId") String familyId);

    int revokeFamilyTokens(@Param("familyId") String familyId);

    int deleteExpiredBefore(@Param("cutoff") Instant cutoff);
}
