package com.nowcoder.community.user.infrastructure.persistence.mapper;

import com.nowcoder.community.user.infrastructure.persistence.dataobject.RefreshTokenSessionDataObject;
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
            @Param("expiresAt") Instant expiresAt
    );

    RefreshTokenSessionDataObject selectByTokenHash(@Param("tokenHash") String tokenHash);

    int consumeActive(@Param("tokenHash") String tokenHash, @Param("now") Instant now);

    int revoke(@Param("tokenHash") String tokenHash);

    int upsertFamilyRevocation(@Param("familyId") String familyId);

    int revokeFamilyTokens(@Param("familyId") String familyId);

    int upsertUserFamilyRevocations(@Param("userId") UUID userId);

    int revokeUserTokens(@Param("userId") UUID userId);

    int deleteExpiredBefore(@Param("cutoff") Instant cutoff);
}
