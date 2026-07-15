package com.nowcoder.community.content.application;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.id.BinaryUuidCodec;
import com.nowcoder.community.common.web.net.ClientIpResolver;
import com.nowcoder.community.content.application.command.PostMediaReferenceCommand;
import com.nowcoder.community.content.domain.model.PostMediaAsset;
import com.nowcoder.community.content.domain.model.PostMediaReferenceOperation;
import com.nowcoder.community.content.infrastructure.persistence.MyBatisPostMediaAssetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest(classes = CommunityAppApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class PostMediaReferenceTransactionBoundaryTest {

    private static final UUID ASSET_ID = UUID.fromString("00000000-0000-7000-8000-000000006001");
    private static final UUID OWNER_ID = UUID.fromString("00000000-0000-7000-8000-000000006002");
    private static final UUID POST_ID = UUID.fromString("00000000-0000-7000-8000-000000006003");
    private static final UUID OBJECT_ID = UUID.fromString("00000000-0000-7000-8000-000000006004");
    private static final UUID VERSION_ID = UUID.fromString("00000000-0000-7000-8000-000000006005");
    private static final UUID REFERENCE_ID = UUID.fromString("00000000-0000-7000-8000-000000006006");
    private static final UUID ACTOR_USER_ID = UUID.fromString("00000000-0000-7000-8000-000000006007");
    private static final long BIND_VERSION = 1L;
    private static final long RELEASE_VERSION = 2L;
    private static final Date NOW = Date.from(Instant.parse("2026-07-15T06:00:00Z"));

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PostMediaReferenceApplicationService service;

    @SpyBean
    private MyBatisPostMediaAssetRepository repository;

    @MockBean
    private PostMediaStoragePort storage;

    @MockBean
    private ClientIpResolver clientIpResolver;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from post_media_asset");
        insertBindPendingAsset();
    }

    @Test
    void bindAndReleaseRemoteCallsShouldRunBetweenShortDatabaseTransactions() {
        AtomicInteger transactionalRepositoryCalls = new AtomicInteger();
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            transactionalRepositoryCalls.incrementAndGet();
            return invocation.callRealMethod();
        }).when(repository).getRequired(ASSET_ID);
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            transactionalRepositoryCalls.incrementAndGet();
            return invocation.callRealMethod();
        }).when(repository).markBound(eq(ASSET_ID), eq(BIND_VERSION), any(Date.class));
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            transactionalRepositoryCalls.incrementAndGet();
            return invocation.callRealMethod();
        }).when(repository).markReleased(eq(ASSET_ID), eq(RELEASE_VERSION), any(Date.class));
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return REFERENCE_ID;
        }).when(storage).bindReference(
                any(PostMediaAsset.class),
                eq(POST_ID),
                eq(REFERENCE_ID),
                eq(ACTOR_USER_ID)
        );
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return null;
        }).when(storage).releaseReference(any(PostMediaAsset.class), eq(ACTOR_USER_ID));

        service.process(new PostMediaReferenceCommand(
                ASSET_ID,
                PostMediaReferenceOperation.BIND,
                BIND_VERSION,
                ACTOR_USER_ID
        ));
        long releaseVersion = repository.requestRelease(ASSET_ID, NOW);
        service.process(new PostMediaReferenceCommand(
                ASSET_ID,
                PostMediaReferenceOperation.RELEASE,
                releaseVersion,
                ACTOR_USER_ID
        ));

        assertThat(releaseVersion).isEqualTo(RELEASE_VERSION);
        assertThat(transactionalRepositoryCalls).hasValue(4);
        assertThat(referenceStatus()).isEqualTo("RELEASED");
    }

    private void insertBindPendingAsset() {
        jdbcTemplate.update(
                "insert into post_media_asset(" +
                        "id, owner_user_id, post_id, oss_object_id, oss_version_id, oss_reference_id, " +
                        "file_name, content_type, content_length, media_kind, lifecycle, video_state, public_url, " +
                        "failure_reason, reference_status, reference_operation_version, reference_updated_at, create_time" +
                        ") values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                BinaryUuidCodec.toBytes(ASSET_ID),
                BinaryUuidCodec.toBytes(OWNER_ID),
                BinaryUuidCodec.toBytes(POST_ID),
                BinaryUuidCodec.toBytes(OBJECT_ID),
                BinaryUuidCodec.toBytes(VERSION_ID),
                BinaryUuidCodec.toBytes(REFERENCE_ID),
                "cover.png",
                "image/png",
                256L,
                "IMAGE",
                "UPLOADED",
                "NONE",
                "https://cdn.example.com/cover.png",
                "",
                "BIND_PENDING",
                BIND_VERSION,
                Timestamp.from(NOW.toInstant()),
                Timestamp.from(NOW.toInstant())
        );
    }

    private String referenceStatus() {
        return jdbcTemplate.queryForObject(
                "select reference_status from post_media_asset where id = ?",
                String.class,
                BinaryUuidCodec.toBytes(ASSET_ID)
        );
    }
}
