package com.nowcoder.community.oss.domain.service;

import com.nowcoder.community.oss.domain.model.OssAccessGrant;
import com.nowcoder.community.oss.domain.model.OssObject;
import com.nowcoder.community.oss.domain.model.OssVisibility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class OssObjectAccessPolicyTest {

    private static final Instant NOW = Instant.parse("2026-07-19T00:00:00Z");
    private static final UUID OBJECT_ID = uuid(1);
    private static final UUID VERSION_ID = uuid(2);
    private static final UUID OTHER_VERSION_ID = uuid(3);
    private static final String OWNER_ID = "user-owner";
    private static final String GRANTEE_ID = "user-reader";

    private final OssObjectAccessPolicy policy = new OssObjectAccessPolicy();

    @Test
    void userOwnerCanReadAndManageObject() {
        OssObject object = object(OBJECT_ID, "USER", OWNER_ID);

        assertThat(policy.canRead(object, VERSION_ID, OWNER_ID, List.of(), NOW)).isTrue();
        assertThat(policy.canManage(object, OWNER_ID)).isTrue();
    }

    @Test
    void matchingOwnerIdWithoutUserOwnerTypeDoesNotGrantAccess() {
        OssObject object = object(OBJECT_ID, "SERVICE", OWNER_ID);

        assertThat(policy.canRead(object, VERSION_ID, OWNER_ID, List.of(), NOW)).isFalse();
        assertThat(policy.canManage(object, OWNER_ID)).isFalse();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "   ")
    void emptyActorCannotReadOrManage(String actorId) {
        OssObject object = object(OBJECT_ID, "USER", OWNER_ID);
        OssAccessGrant grant = readGrant(OBJECT_ID, null, GRANTEE_ID, NOW.plusSeconds(300));

        assertThat(policy.canRead(object, VERSION_ID, actorId, List.of(grant), NOW)).isFalse();
        assertThat(policy.canManage(object, actorId)).isFalse();
    }

    @Test
    void activeObjectWideUserReadGrantAllowsReadButNotManagement() {
        OssObject object = object(OBJECT_ID, "USER", OWNER_ID);
        OssAccessGrant grant = readGrant(OBJECT_ID, null, GRANTEE_ID, NOW.plusSeconds(300));

        assertThat(policy.canRead(object, VERSION_ID, GRANTEE_ID, List.of(grant), NOW)).isTrue();
        assertThat(policy.canRead(object, OTHER_VERSION_ID, GRANTEE_ID, List.of(grant), NOW)).isTrue();
        assertThat(policy.canManage(object, GRANTEE_ID)).isFalse();
    }

    @ParameterizedTest(name = "{0}")
    @org.junit.jupiter.params.provider.MethodSource("invalidReadGrants")
    void invalidGrantDoesNotAllowRead(String description, OssAccessGrant grant, String actorId) {
        OssObject object = object(OBJECT_ID, "USER", OWNER_ID);

        assertThat(policy.canRead(object, VERSION_ID, actorId, List.of(grant), NOW))
                .as(description)
                .isFalse();
    }

    @Test
    void versionSpecificGrantOnlyAllowsItsVersion() {
        OssObject object = object(OBJECT_ID, "USER", OWNER_ID);
        OssAccessGrant grant = readGrant(OBJECT_ID, VERSION_ID, GRANTEE_ID, NOW.plusSeconds(300));

        assertThat(policy.canRead(object, VERSION_ID, GRANTEE_ID, List.of(grant), NOW)).isTrue();
        assertThat(policy.canRead(object, OTHER_VERSION_ID, GRANTEE_ID, List.of(grant), NOW)).isFalse();
        assertThat(policy.canRead(object, null, GRANTEE_ID, List.of(grant), NOW)).isFalse();
    }

    private static Stream<Arguments> invalidReadGrants() {
        OssAccessGrant active = readGrant(OBJECT_ID, null, GRANTEE_ID, NOW.plusSeconds(300));
        return Stream.of(
                Arguments.of("revoked grant", active.revoke(NOW.minusSeconds(1)), GRANTEE_ID),
                Arguments.of("expired grant", readGrant(OBJECT_ID, null, GRANTEE_ID, NOW), GRANTEE_ID),
                Arguments.of("different principal", readGrant(OBJECT_ID, null, GRANTEE_ID, NOW.plusSeconds(300)), "other-user"),
                Arguments.of("non-user principal", grant(OBJECT_ID, null, "SERVICE", GRANTEE_ID, "READ"), GRANTEE_ID),
                Arguments.of("non-read permission", grant(OBJECT_ID, null, "USER", GRANTEE_ID, "WRITE"), GRANTEE_ID),
                Arguments.of("different object", readGrant(uuid(99), null, GRANTEE_ID, NOW.plusSeconds(300)), GRANTEE_ID)
        );
    }

    private static OssObject object(UUID objectId, String ownerType, String ownerId) {
        return OssObject.stage(
                objectId,
                "ATTACHMENT",
                "community-app",
                "content",
                ownerType,
                ownerId,
                OssVisibility.PRIVATE,
                OWNER_ID,
                NOW
        );
    }

    private static OssAccessGrant readGrant(
            UUID objectId,
            UUID versionId,
            String principalValue,
            Instant expiresAt
    ) {
        return OssAccessGrant.readGrant(
                uuid(10),
                objectId,
                versionId,
                "USER",
                principalValue,
                OWNER_ID,
                NOW.minusSeconds(60),
                expiresAt
        );
    }

    private static OssAccessGrant grant(
            UUID objectId,
            UUID versionId,
            String principalType,
            String principalValue,
            String permission
    ) {
        return new OssAccessGrant(
                uuid(11),
                objectId,
                versionId,
                principalType,
                principalValue,
                permission,
                NOW.plusSeconds(300),
                OWNER_ID,
                NOW.minusSeconds(60),
                null
        );
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }
}
