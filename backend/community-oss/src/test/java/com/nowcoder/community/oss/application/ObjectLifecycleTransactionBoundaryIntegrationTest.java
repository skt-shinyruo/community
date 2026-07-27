package com.nowcoder.community.oss.application;

import com.nowcoder.community.oss.application.command.DeleteObjectCommand;
import com.nowcoder.community.oss.application.port.ObjectDeletePort;
import com.nowcoder.community.oss.domain.model.OssObject;
import com.nowcoder.community.oss.domain.model.OssObjectStatus;
import com.nowcoder.community.oss.domain.model.OssObjectVersion;
import com.nowcoder.community.oss.domain.model.OssVisibility;
import com.nowcoder.community.oss.domain.repository.OssObjectRepository;
import com.nowcoder.community.oss.domain.repository.OssObjectVersionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:oss-delete-boundary;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.sql.init.mode=never",
        "spring.task.scheduling.enabled=false",
        "security.jwt.hmac-secret=01234567890123456789012345678901",
        "security.jwt.issuer=community-oss-delete-test",
        "spring.cloud.discovery.enabled=false",
        "spring.cloud.nacos.discovery.enabled=false",
        "spring.cloud.nacos.config.enabled=false",
        "oss.object-store.mode=local",
        "oss.object-store.local-root=${java.io.tmpdir}/community-oss-delete-boundary"
})
class ObjectLifecycleTransactionBoundaryIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-07-27T00:00:00Z");

    @Autowired
    private ObjectLifecycleApplicationService applicationService;

    @Autowired
    private ObjectDeletionRecoveryApplicationService recoveryApplicationService;

    @Autowired
    private OssObjectRepository objectRepository;

    @Autowired
    private OssObjectVersionRepository versionRepository;

    @Autowired
    private TransactionObservingDeletePort deletePort;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void resetSchema() {
        deletePort.reset();
        jdbc.execute("drop table if exists oss_access_grant");
        jdbc.execute("drop table if exists oss_object_reference");
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
                create table oss_object_reference (
                  reference_id binary(16) primary key,
                  object_id binary(16) not null,
                  version_id binary(16),
                  subject_service varchar(64) not null,
                  subject_domain varchar(64) not null,
                  subject_type varchar(64) not null,
                  subject_id varchar(128) not null,
                  reference_role varchar(64) not null,
                  status varchar(32) not null,
                  retain_until timestamp,
                  created_at timestamp not null,
                  released_at timestamp
                )
                """);
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
    void externalDeleteMustRunAfterDurableClaimAndWithoutActiveDatabaseTransaction() {
        UUID objectId = uuid(1);
        seedActiveObject(objectId, uuid(2), "upload-1");
        deletePort.objectId = objectId;

        var result = applicationService.deleteInternalObject(
                new DeleteObjectCommand(objectId, "user-1"), "community-app");

        assertThat(result.purged()).isTrue();
        assertThat(deletePort.transactionActive).isFalse();
        assertThat(deletePort.statusObservedDuringDelete).isEqualTo("DELETE_PENDING");
        assertThat(objectRepository.findById(objectId)).get()
                .extracting(OssObject::status).isEqualTo(OssObjectStatus.PURGED);
    }

    @Test
    void recoveryDeleteMustAlsoRunWithoutActiveDatabaseTransaction() {
        UUID objectId = uuid(10);
        seedActiveObject(objectId, uuid(11), "upload-10");
        OssObject active = objectRepository.findById(objectId).orElseThrow();
        objectRepository.save(active.deletePending(NOW.minusSeconds(600)));
        deletePort.objectId = objectId;

        recoveryApplicationService.recoverPendingDeletions(NOW.minusSeconds(300), 10);

        assertThat(deletePort.transactionActive).isFalse();
        assertThat(deletePort.statusObservedDuringDelete).isEqualTo("DELETE_PENDING");
        assertThat(objectRepository.findById(objectId)).get()
                .extracting(OssObject::status).isEqualTo(OssObjectStatus.PURGED);
    }

    private void seedActiveObject(UUID objectId, UUID versionId, String ownerId) {
        OssObjectVersion version = OssObjectVersion.staged(
                versionId, objectId, "S3_COMPATIBLE", "community-oss", "objects/delete-me",
                "delete-me.bin", "application/octet-stream", 4L, "sha256", NOW)
                .activate("etag", NOW);
        objectRepository.save(OssObject.stage(
                objectId, "DRIVE_FILE", "community-app", "drive", "DRIVE_UPLOAD", ownerId,
                OssVisibility.SIGNED, "user-1", NOW).activate(version, NOW));
        versionRepository.save(version);
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }

    @TestConfiguration
    static class DeletePortConfiguration {

        @Bean
        @Primary
        TransactionObservingDeletePort transactionObservingDeletePort(JdbcTemplate jdbc) {
            return new TransactionObservingDeletePort(jdbc);
        }
    }

    static final class TransactionObservingDeletePort implements ObjectDeletePort {
        private final JdbcTemplate jdbc;
        private UUID objectId;
        private boolean transactionActive;
        private String statusObservedDuringDelete;

        TransactionObservingDeletePort(JdbcTemplate jdbc) {
            this.jdbc = jdbc;
        }

        @Override
        public void deleteIfExists(String bucket, String key) {
            transactionActive = TransactionSynchronizationManager.isActualTransactionActive();
            statusObservedDuringDelete = jdbc.queryForObject(
                    "select status from oss_object where object_id = ?", String.class, objectId);
        }

        void reset() {
            objectId = null;
            transactionActive = false;
            statusObservedDuringDelete = null;
        }
    }
}
