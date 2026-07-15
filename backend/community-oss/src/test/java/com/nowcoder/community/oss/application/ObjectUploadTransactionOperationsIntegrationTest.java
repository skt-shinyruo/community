package com.nowcoder.community.oss.application;

import com.nowcoder.community.oss.domain.model.OssObject;
import com.nowcoder.community.oss.domain.model.OssObjectVersion;
import com.nowcoder.community.oss.domain.model.OssUploadSession;
import com.nowcoder.community.oss.domain.model.OssVisibility;
import com.nowcoder.community.oss.domain.repository.OssObjectRepository;
import com.nowcoder.community.oss.domain.repository.OssObjectVersionRepository;
import com.nowcoder.community.oss.domain.repository.OssUploadSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:oss-upload-fencing;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.sql.init.mode=never",
        "spring.task.scheduling.enabled=false",
        "security.jwt.hmac-secret=01234567890123456789012345678901",
        "security.jwt.issuer=community-oss-transaction-test",
        "spring.cloud.discovery.enabled=false",
        "spring.cloud.nacos.discovery.enabled=false",
        "spring.cloud.nacos.config.enabled=false",
        "oss.object-store.mode=local",
        "oss.object-store.local-root=${java.io.tmpdir}/community-oss-transaction-test"
})
class ObjectUploadTransactionOperationsIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-07-15T00:00:00Z");

    @Autowired
    private ObjectUploadTransactionOperations operations;

    @Autowired
    private OssObjectRepository objectRepository;

    @Autowired
    private OssObjectVersionRepository versionRepository;

    @Autowired
    private OssUploadSessionRepository sessionRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void resetSchema() {
        jdbc.execute("drop table if exists oss_upload_session");
        jdbc.execute("drop table if exists oss_object_version");
        jdbc.execute("drop table if exists oss_object");
        jdbc.execute("""
                create table oss_object (
                  object_id binary(16) primary key,
                  `usage` varchar(64) not null,
                  owner_service varchar(64) not null,
                  owner_domain varchar(64) not null,
                  owner_type varchar(64) not null,
                  owner_id varchar(128) not null,
                  visibility varchar(32) not null,
                  status varchar(32) not null,
                  current_version_id binary(16),
                  latest_file_name varchar(255) not null,
                  latest_content_type varchar(128) not null,
                  latest_content_length bigint not null,
                  latest_checksum_sha256 varchar(128) not null,
                  created_by varchar(128) not null,
                  created_at timestamp not null,
                  updated_at timestamp not null
                )
                """);
        jdbc.execute("""
                create table oss_object_version (
                  version_id binary(16) primary key,
                  object_id binary(16) not null,
                  version_no int not null,
                  storage_backend varchar(64) not null,
                  storage_bucket varchar(128) not null,
                  storage_key varchar(1024) not null,
                  status varchar(32) not null,
                  file_name varchar(255) not null,
                  content_type varchar(128) not null,
                  content_length bigint not null,
                  checksum_sha256 varchar(128) not null,
                  etag varchar(255) not null,
                  cache_control varchar(255) not null,
                  content_disposition varchar(255) not null,
                  source_object_id binary(16),
                  variant_type varchar(64) not null,
                  created_at timestamp not null,
                  activated_at timestamp,
                  expired_at timestamp,
                  purged_at timestamp
                )
                """);
        jdbc.execute("""
                create table oss_upload_session (
                  session_id binary(16) primary key,
                  request_id binary(16) not null unique,
                  object_id binary(16) not null,
                  version_id binary(16) not null,
                  upload_mode varchar(32) not null,
                  owner_service varchar(64) not null,
                  owner_domain varchar(64) not null,
                  owner_type varchar(64) not null,
                  owner_id varchar(128) not null,
                  expected_file_name varchar(255) not null,
                  expected_content_type varchar(128) not null,
                  expected_content_length bigint not null,
                  expected_checksum_sha256 varchar(128) not null,
                  status varchar(32) not null,
                  claim_version bigint not null default 0,
                  expires_at timestamp not null,
                  created_by varchar(128) not null,
                  created_at timestamp not null,
                  updated_at timestamp not null,
                  completed_at timestamp,
                  last_error varchar(512) not null default ''
                )
                """);
    }

    @Test
    void staleFinalizeMustFailBeforeMutatingCanonicalMetadata() {
        Fixture fixture = seed(7500);
        OssUploadSession staleClaim = operations.claimCompletion(fixture.session().sessionId(), NOW.plusSeconds(1))
                .orElseThrow();
        assertThat(operations.resetFailedClaim(
                staleClaim.sessionId(),
                staleClaim.claimVersion(),
                NOW.plusSeconds(2),
                NOW.plusSeconds(902)
        )).isTrue();
        OssObjectVersion lateVersion = fixture.version()
                .withUploadedContentAt("attempt/stale", "image/png", 4L, "sha256-post")
                .activate("late-etag", NOW.plusSeconds(3));
        OssObject lateObject = fixture.object().activate(lateVersion, NOW.plusSeconds(3));

        assertThatThrownBy(() -> operations.finalizeUpload(
                lateVersion, lateObject, staleClaim.complete(NOW.plusSeconds(3))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("claim");

        assertThat(value("select status from oss_object where object_id = ?", fixture.object().objectId()))
                .isEqualTo("STAGED");
        assertThat(value("select status from oss_object_version where version_id = ?", fixture.version().versionId()))
                .isEqualTo("STAGED");
        assertThat(value("select status from oss_upload_session where session_id = ?", fixture.session().sessionId()))
                .isEqualTo("READY");
    }

    @Test
    void metadataSaveFailureMustRollBackTheEarlierCompleteClaimCas() {
        Fixture fixture = seed(7600);
        OssUploadSession claim = operations.claimCompletion(fixture.session().sessionId(), NOW.plusSeconds(1))
                .orElseThrow();
        OssObjectVersion invalidVersion = fixture.version()
                .withUploadedContentAt("x".repeat(1100), "image/png", 4L, "sha256-post")
                .activate("etag", NOW.plusSeconds(2));
        OssObject activeObject = fixture.object().activate(invalidVersion, NOW.plusSeconds(2));

        assertThatThrownBy(() -> operations.finalizeUpload(
                invalidVersion, activeObject, claim.complete(NOW.plusSeconds(2))))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(value("select status from oss_upload_session where session_id = ?", fixture.session().sessionId()))
                .isEqualTo("UPLOADING");
        assertThat(value("select status from oss_object_version where version_id = ?", fixture.version().versionId()))
                .isEqualTo("STAGED");
        assertThat(AopUtils.isAopProxy(operations)).isTrue();
    }

    @Test
    void objectSaveFailureMustRollBackTheCompletedClaimAndTheEarlierVersionSave() {
        Fixture fixture = seed(7700);
        OssUploadSession claim = operations.claimCompletion(fixture.session().sessionId(), NOW.plusSeconds(1))
                .orElseThrow();
        OssObjectVersion activeVersion = fixture.version()
                .withUploadedContentAt("attempt/winner", "image/png", 4L, "sha256-post")
                .activate("etag", NOW.plusSeconds(2));
        OssObject activated = fixture.object().activate(activeVersion, NOW.plusSeconds(2));
        OssObject invalidObject = new OssObject(
                activated.objectId(),
                activated.usage(),
                activated.ownerService(),
                activated.ownerDomain(),
                activated.ownerType(),
                "x".repeat(129),
                activated.visibility(),
                activated.status(),
                activated.currentVersionId(),
                activated.latestFileName(),
                activated.latestContentType(),
                activated.latestContentLength(),
                activated.latestChecksumSha256(),
                activated.createdBy(),
                activated.createdAt(),
                activated.updatedAt()
        );

        assertThatThrownBy(() -> operations.finalizeUpload(
                activeVersion, invalidObject, claim.complete(NOW.plusSeconds(2))))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(value("select status from oss_upload_session where session_id = ?", fixture.session().sessionId()))
                .isEqualTo("UPLOADING");
        assertThat(value("select status from oss_object_version where version_id = ?", fixture.version().versionId()))
                .isEqualTo("STAGED");
        assertThat(value("select storage_key from oss_object_version where version_id = ?", fixture.version().versionId()))
                .isEqualTo(fixture.version().storageKey());
        assertThat(value("select status from oss_object where object_id = ?", fixture.object().objectId()))
                .isEqualTo("STAGED");
        assertThat(jdbc.queryForObject(
                "select current_version_id from oss_object where object_id = ?",
                byte[].class,
                fixture.object().objectId()
        )).isNull();
    }

    private Fixture seed(long suffix) {
        UUID objectId = uuid(suffix + 1);
        UUID versionId = uuid(suffix + 2);
        OssObject object = OssObject.stage(
                objectId, "CONTENT_POST_MEDIA", "community-app", "content", "post-media-draft",
                "asset-" + suffix, OssVisibility.PUBLIC, "actor-" + suffix, NOW);
        OssObjectVersion version = OssObjectVersion.staged(
                versionId, objectId, "S3_COMPATIBLE", "community-oss",
                "objects/" + objectId + "/" + versionId + "/post.png",
                "post.png", "image/png", 4L, "sha256-post", NOW);
        OssUploadSession session = OssUploadSession.ready(
                uuid(suffix + 3), objectId, versionId, "PROXY", "community-app", "content",
                "post-media-draft", "asset-" + suffix, "post.png", "image/png", 4L,
                "sha256-post", "actor-" + suffix, NOW, NOW.plusSeconds(900));
        assertThat(operations.createPreparedUpload(object, version, session)).isTrue();
        return new Fixture(object, version, session);
    }

    private String value(String sql, UUID id) {
        return jdbc.queryForObject(sql, String.class, id);
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }

    private record Fixture(OssObject object, OssObjectVersion version, OssUploadSession session) {
    }
}
