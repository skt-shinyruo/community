package com.nowcoder.community.content.infrastructure.persistence;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.id.BinaryUuidCodec;
import com.nowcoder.community.common.web.net.ClientIpResolver;
import com.nowcoder.community.content.domain.model.PostMediaAsset;
import com.nowcoder.community.content.domain.model.PostVideoState;
import com.nowcoder.community.content.domain.repository.PostMediaAssetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = CommunityAppApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class PostMediaReferenceRepositoryPersistenceTest {

    private static final UUID ASSET_ID = UUID.fromString("00000000-0000-7000-8000-000000001001");
    private static final UUID OWNER_ID = UUID.fromString("00000000-0000-7000-8000-000000001002");
    private static final UUID POST_ID = UUID.fromString("00000000-0000-7000-8000-000000001003");
    private static final UUID OBJECT_ID = UUID.fromString("00000000-0000-7000-8000-000000001004");
    private static final UUID VERSION_ID = UUID.fromString("00000000-0000-7000-8000-000000001005");
    private static final UUID REFERENCE_ID = UUID.fromString("00000000-0000-7000-8000-000000001006");

    private static final Date CREATED_AT = at("2026-07-15T02:00:00Z");
    private static final Date BIND_REQUESTED_AT = at("2026-07-15T02:01:00Z");
    private static final Date BOUND_AT = at("2026-07-15T02:02:00Z");
    private static final Date RELEASE_REQUESTED_AT = at("2026-07-15T02:03:00Z");
    private static final Date RELEASED_AT = at("2026-07-15T02:04:00Z");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PostMediaAssetRepository repository;

    @MockBean
    private ClientIpResolver clientIpResolver;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from post_media_asset");
        insertUploadedAsset();
    }

    @Test
    void bindAndReleaseShouldPersistDesiredStateAndIncrementVersionPerOperation() {
        long bindVersion = repository.requestBind(
                ASSET_ID,
                POST_ID,
                REFERENCE_ID,
                PostVideoState.PENDING_TRANSCODE,
                BIND_REQUESTED_AT
        );

        assertThat(bindVersion).isEqualTo(1L);
        assertReferenceState("BIND_PENDING", 1L, BIND_REQUESTED_AT);
        assertThat(referenceId()).isEqualTo(REFERENCE_ID);
        assertThat(repository.listPending(10))
                .extracting(PostMediaAsset::id)
                .containsExactly(ASSET_ID);

        assertThat(repository.markBound(ASSET_ID, bindVersion, BOUND_AT)).isTrue();
        assertReferenceState("BOUND", 1L, BOUND_AT);
        assertThat(lifecycle()).isEqualTo("BOUND");
        assertThat(repository.listPending(10)).isEmpty();

        long releaseVersion = repository.requestRelease(ASSET_ID, RELEASE_REQUESTED_AT);

        assertThat(releaseVersion).isEqualTo(2L);
        assertReferenceState("RELEASE_PENDING", 2L, RELEASE_REQUESTED_AT);
        assertThat(lifecycle()).isEqualTo("BOUND");
        assertThat(referenceId()).isEqualTo(REFERENCE_ID);
        assertThat(repository.listPending(10))
                .extracting(PostMediaAsset::id)
                .containsExactly(ASSET_ID);

        assertThat(repository.markReleased(ASSET_ID, releaseVersion, RELEASED_AT)).isTrue();
        assertReferenceState("RELEASED", 2L, RELEASED_AT);
        assertThat(lifecycle()).isEqualTo("RELEASED");
        assertThat(repository.listPending(10)).isEmpty();
    }

    @Test
    void staleVersionMustNotFinalizeANewerBindOperation() {
        long currentVersion = repository.requestBind(
                ASSET_ID,
                POST_ID,
                REFERENCE_ID,
                PostVideoState.NONE,
                BIND_REQUESTED_AT
        );

        assertThat(repository.markBound(ASSET_ID, currentVersion - 1L, BOUND_AT)).isFalse();

        assertReferenceState("BIND_PENDING", currentVersion, BIND_REQUESTED_AT);
        assertThat(lifecycle()).isEqualTo("UPLOADED");
        assertThat(referenceId()).isEqualTo(REFERENCE_ID);
    }

    @Test
    void staleVersionMustNotFinalizeANewerReleaseOperationOrDropItsReferenceId() {
        long bindVersion = repository.requestBind(
                ASSET_ID,
                POST_ID,
                REFERENCE_ID,
                PostVideoState.NONE,
                BIND_REQUESTED_AT
        );
        assertThat(repository.markBound(ASSET_ID, bindVersion, BOUND_AT)).isTrue();
        long releaseVersion = repository.requestRelease(ASSET_ID, RELEASE_REQUESTED_AT);

        assertThat(repository.markReleased(ASSET_ID, releaseVersion - 1L, RELEASED_AT)).isFalse();

        assertReferenceState("RELEASE_PENDING", releaseVersion, RELEASE_REQUESTED_AT);
        assertThat(lifecycle()).isEqualTo("BOUND");
        assertThat(referenceId()).isEqualTo(REFERENCE_ID);
    }

    private void insertUploadedAsset() {
        jdbcTemplate.update(
                "insert into post_media_asset(" +
                        "id, owner_user_id, post_id, oss_object_id, oss_version_id, oss_reference_id, " +
                        "file_name, content_type, content_length, media_kind, lifecycle, video_state, public_url, " +
                        "failure_reason, reference_status, reference_operation_version, reference_updated_at, create_time" +
                        ") values (?, ?, null, ?, ?, null, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                BinaryUuidCodec.toBytes(ASSET_ID),
                BinaryUuidCodec.toBytes(OWNER_ID),
                BinaryUuidCodec.toBytes(OBJECT_ID),
                BinaryUuidCodec.toBytes(VERSION_ID),
                "demo.mp4",
                "video/mp4",
                1234L,
                "VIDEO",
                "UPLOADED",
                "NONE",
                "https://cdn.example.com/demo.mp4",
                "",
                "UNBOUND",
                0L,
                Timestamp.from(CREATED_AT.toInstant()),
                Timestamp.from(CREATED_AT.toInstant())
        );
    }

    private void assertReferenceState(String status, long operationVersion, Date updatedAt) {
        assertThat(referenceStatus()).isEqualTo(status);
        assertThat(referenceOperationVersion()).isEqualTo(operationVersion);
        assertThat(referenceUpdatedAt()).isEqualTo(updatedAt.toInstant());
    }

    private String lifecycle() {
        return jdbcTemplate.queryForObject(
                "select lifecycle from post_media_asset where id = ?",
                String.class,
                BinaryUuidCodec.toBytes(ASSET_ID)
        );
    }

    private String referenceStatus() {
        return jdbcTemplate.queryForObject(
                "select reference_status from post_media_asset where id = ?",
                String.class,
                BinaryUuidCodec.toBytes(ASSET_ID)
        );
    }

    private long referenceOperationVersion() {
        Long value = jdbcTemplate.queryForObject(
                "select reference_operation_version from post_media_asset where id = ?",
                Long.class,
                BinaryUuidCodec.toBytes(ASSET_ID)
        );
        return value == null ? -1L : value;
    }

    private Instant referenceUpdatedAt() {
        Timestamp value = jdbcTemplate.queryForObject(
                "select reference_updated_at from post_media_asset where id = ?",
                Timestamp.class,
                BinaryUuidCodec.toBytes(ASSET_ID)
        );
        return value == null ? null : value.toInstant();
    }

    private UUID referenceId() {
        byte[] value = jdbcTemplate.queryForObject(
                "select oss_reference_id from post_media_asset where id = ?",
                byte[].class,
                BinaryUuidCodec.toBytes(ASSET_ID)
        );
        return value == null ? null : BinaryUuidCodec.fromBytes(value);
    }

    private static Date at(String value) {
        return Date.from(Instant.parse(value));
    }
}
