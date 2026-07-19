package com.nowcoder.community.oss.infrastructure.persistence;

import com.nowcoder.community.oss.domain.model.OssAccessGrant;
import com.nowcoder.community.oss.domain.repository.OssAccessGrantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:oss-access-grants;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.sql.init.mode=never",
        "spring.task.scheduling.enabled=false",
        "security.jwt.hmac-secret=01234567890123456789012345678901",
        "security.jwt.issuer=community-oss-access-grant-test",
        "spring.cloud.discovery.enabled=false",
        "spring.cloud.nacos.discovery.enabled=false",
        "spring.cloud.nacos.config.enabled=false",
        "oss.object-store.mode=local",
        "oss.object-store.local-root=${java.io.tmpdir}/community-oss-access-grant-test"
})
class MyBatisOssAccessGrantRepositoryIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-07-19T00:00:00Z");

    @Autowired
    private OssAccessGrantRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void resetSchema() {
        jdbc.execute("drop table if exists oss_access_grant");
        jdbc.execute("""
                create table oss_access_grant (
                  grant_id binary(16) primary key,
                  object_id binary(16) not null,
                  version_id binary(16),
                  principal_type varchar(32) not null,
                  principal_value varchar(128) not null,
                  permission varchar(32) not null,
                  expires_at timestamp,
                  created_by varchar(128) not null,
                  created_at timestamp not null,
                  revoked_at timestamp
                )
                """);
    }

    @Test
    void findReadGrantsShouldReturnOnlyObjectWideAndMatchingVersionCandidates() {
        UUID objectId = uuid(8100);
        UUID versionId = uuid(8101);
        OssAccessGrant objectWide = readGrant(uuid(8110), objectId, null, "reader-7", null);
        OssAccessGrant matchingVersion = readGrant(
                uuid(8111), objectId, versionId, "reader-7", NOW.plusSeconds(300));
        OssAccessGrant expired = readGrant(
                uuid(8112), objectId, versionId, "reader-7", NOW.minusSeconds(1));
        OssAccessGrant revoked = readGrant(
                uuid(8113), objectId, null, "reader-7", NOW.plusSeconds(300)).revoke(NOW.minusSeconds(1));
        OssAccessGrant foreignVersion = readGrant(
                uuid(8114), objectId, uuid(8190), "reader-7", NOW.plusSeconds(300));
        OssAccessGrant foreignPrincipal = readGrant(
                uuid(8115), objectId, versionId, "reader-8", NOW.plusSeconds(300));
        OssAccessGrant foreignPrincipalType = new OssAccessGrant(
                uuid(8116), objectId, versionId, "SERVICE", "reader-7", "READ",
                NOW.plusSeconds(300), "owner-7", NOW.minusSeconds(60), null);
        OssAccessGrant foreignPermission = new OssAccessGrant(
                uuid(8117), objectId, versionId, "USER", "reader-7", "WRITE",
                NOW.plusSeconds(300), "owner-7", NOW.minusSeconds(60), null);
        List.of(
                objectWide,
                matchingVersion,
                expired,
                revoked,
                foreignVersion,
                foreignPrincipal,
                foreignPrincipalType,
                foreignPermission
        ).forEach(repository::save);

        List<OssAccessGrant> candidates = repository.findReadGrants(
                objectId, versionId, "  reader-7  ");

        assertThat(candidates)
                .extracting(OssAccessGrant::grantId)
                .containsExactlyInAnyOrder(
                        objectWide.grantId(),
                        matchingVersion.grantId(),
                        expired.grantId(),
                        revoked.grantId()
                );
        assertThat(candidates).filteredOn(grant -> !grant.activeAt(NOW)).hasSize(2);
    }

    private static OssAccessGrant readGrant(
            UUID grantId,
            UUID objectId,
            UUID versionId,
            String principalValue,
            Instant expiresAt
    ) {
        return OssAccessGrant.readGrant(
                grantId,
                objectId,
                versionId,
                "USER",
                principalValue,
                "owner-7",
                NOW.minusSeconds(60),
                expiresAt
        );
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }
}
