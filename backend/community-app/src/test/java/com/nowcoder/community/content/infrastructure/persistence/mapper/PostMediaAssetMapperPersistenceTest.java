package com.nowcoder.community.content.infrastructure.persistence.mapper;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.web.net.ClientIpResolver;
import com.nowcoder.community.content.domain.model.PostMediaAsset;
import com.nowcoder.community.content.domain.model.PostMediaAssetLifecycle;
import com.nowcoder.community.content.domain.model.PostMediaKind;
import com.nowcoder.community.content.domain.model.PostMediaUploadStatus;
import com.nowcoder.community.content.domain.model.PostVideoState;
import com.nowcoder.community.content.domain.repository.PostMediaAssetRepository;
import com.nowcoder.community.content.infrastructure.persistence.dataobject.PostMediaAssetDataObject;
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
class PostMediaAssetMapperPersistenceTest {

    private static final UUID ASSET_ID = UUID.fromString("00000000-0000-7000-8000-000000000701");
    private static final UUID OWNER_ID = UUID.fromString("00000000-0000-7000-8000-000000000702");
    private static final UUID POST_ID = UUID.fromString("00000000-0000-7000-8000-000000000703");
    private static final UUID OBJECT_ID = UUID.fromString("00000000-0000-7000-8000-000000000704");
    private static final UUID VERSION_ID = UUID.fromString("00000000-0000-7000-8000-000000000705");
    private static final UUID REFERENCE_ID = UUID.fromString("00000000-0000-7000-8000-000000000706");

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    PostMediaAssetMapper mapper;

    @Autowired
    PostMediaAssetRepository repository;

    @MockBean
    ClientIpResolver clientIpResolver;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from post_media_asset");
    }

    @Test
    void insertMarkUploadedAndBindShouldPersistAssetLifecycle() {
        PostMediaAssetDataObject row = new PostMediaAssetDataObject();
        row.setId(ASSET_ID);
        row.setOwnerUserId(OWNER_ID);
        row.setOssObjectId(OBJECT_ID);
        row.setOssVersionId(VERSION_ID);
        row.setUploadSessionId(UUID.fromString("00000000-0000-7000-8000-000000000707"));
        row.setFileName("demo.mp4");
        row.setContentType("video/mp4");
        row.setContentLength(1234L);
        row.setMediaKind("VIDEO");
        row.setLifecycle("DRAFT");
        row.setVideoState("NONE");
        row.setPublicUrl("");
        row.setCreateTime(Timestamp.from(Instant.parse("2026-05-09T00:00:00Z")));

        assertThat(mapper.insert(row)).isEqualTo(1);
        assertThat(mapper.markUploaded(
                ASSET_ID,
                VERSION_ID,
                "http://localhost/files/demo.mp4",
                Timestamp.from(Instant.parse("2026-05-09T00:01:00Z"))
        )).isEqualTo(1);
        assertThat(mapper.bindToPost(
                ASSET_ID,
                POST_ID,
                REFERENCE_ID,
                "PENDING_TRANSCODE",
                Timestamp.from(Instant.parse("2026-05-09T00:02:00Z"))
        )).isEqualTo(1);

        PostMediaAssetDataObject saved = mapper.selectById(ASSET_ID);
        assertThat(saved.getLifecycle()).isEqualTo("BOUND");
        assertThat(saved.getPostId()).isEqualTo(POST_ID);
        assertThat(saved.getOssReferenceId()).isEqualTo(REFERENCE_ID);
        assertThat(saved.getVideoState()).isEqualTo("PENDING_TRANSCODE");
        assertThat(saved.getPublicUrl()).isEqualTo("http://localhost/files/demo.mp4");
    }

    @Test
    void repositoryCreateDraftShouldPersistPreparedUploadSessionContext() {
        UUID uploadSessionId = UUID.fromString("00000000-0000-7000-8000-000000000707");
        PostMediaAsset asset = new PostMediaAsset(
                ASSET_ID,
                OWNER_ID,
                null,
                OBJECT_ID,
                VERSION_ID,
                null,
                uploadSessionId,
                "demo.mp4",
                "video/mp4",
                1234L,
                PostMediaKind.VIDEO,
                PostMediaAssetLifecycle.DRAFT,
                PostVideoState.NONE,
                "",
                "",
                Timestamp.from(Instant.parse("2026-05-09T00:00:00Z")),
                null
        );

        UUID createdId = repository.createDraft(asset);
        PostMediaAsset saved = repository.getRequired(ASSET_ID);

        assertThat(createdId).isEqualTo(ASSET_ID);
        assertThat(saved.id()).isEqualTo(ASSET_ID);
        assertThat(saved.ossObjectId()).isEqualTo(OBJECT_ID);
        assertThat(saved.ossVersionId()).isEqualTo(VERSION_ID);
        assertThat(saved.uploadSessionId()).isEqualTo(uploadSessionId);
        assertThat(saved.lifecycle()).isEqualTo(PostMediaAssetLifecycle.DRAFT);
    }

    @Test
    void markDraftDeletedShouldOnlyDeleteDraftAsset() {
        PostMediaAssetDataObject row = new PostMediaAssetDataObject();
        row.setId(ASSET_ID);
        row.setOwnerUserId(OWNER_ID);
        row.setOssObjectId(OBJECT_ID);
        row.setOssVersionId(VERSION_ID);
        row.setFileName("demo.mp4");
        row.setContentType("video/mp4");
        row.setContentLength(1234L);
        row.setMediaKind("VIDEO");
        row.setLifecycle("DRAFT");
        row.setVideoState("NONE");
        row.setPublicUrl("");
        row.setCreateTime(Timestamp.from(Instant.parse("2026-05-09T00:00:00Z")));
        assertThat(mapper.insert(row)).isEqualTo(1);

        assertThat(mapper.markDraftDeleted(
                ASSET_ID,
                Timestamp.from(Instant.parse("2026-05-09T00:03:00Z"))
        )).isEqualTo(1);

        PostMediaAssetDataObject saved = mapper.selectById(ASSET_ID);
        assertThat(saved.getLifecycle()).isEqualTo("DELETED");
    }

    @Test
    void staleResetMustFenceTheOldCompletionWriterInRealPersistence() {
        Date preparedAt = at("2026-07-15T01:00:00Z");
        Date claimedAt = at("2026-07-15T01:01:00Z");
        Date staleBefore = at("2026-07-15T01:02:00Z");
        Date resetAt = at("2026-07-15T01:03:00Z");
        UUID staleWriterVersionId = UUID.fromString("00000000-0000-7000-8000-000000000708");
        repository.createDraft(preparedAsset(preparedAt));

        assertThat(repository.claimUploadCompletion(ASSET_ID, OWNER_ID, 0L, claimedAt)).isTrue();
        PostMediaAsset claimed = repository.getRequired(ASSET_ID);
        assertThat(claimed.uploadStatus()).isEqualTo(PostMediaUploadStatus.COMPLETING);
        assertThat(claimed.uploadOperationVersion()).isEqualTo(1L);
        assertThat(repository.resetStaleUploadCompletion(
                ASSET_ID, claimed.uploadOperationVersion(), staleBefore, resetAt)).isTrue();

        assertThat(repository.markObjectCompleted(
                ASSET_ID,
                claimed.uploadOperationVersion(),
                staleWriterVersionId,
                "https://cdn.example.test/stale.mp4",
                "video/mp4",
                1234L,
                at("2026-07-15T01:04:00Z")
        )).isFalse();

        PostMediaAsset reset = repository.getRequired(ASSET_ID);
        assertThat(reset.uploadStatus()).isEqualTo(PostMediaUploadStatus.PREPARED);
        assertThat(reset.uploadOperationVersion()).isEqualTo(2L);
        assertThat(reset.uploadUpdatedAt().toInstant()).isEqualTo(resetAt.toInstant());
        assertThat(reset.ossVersionId()).isEqualTo(VERSION_ID);
        assertThat(reset.publicUrl()).isEmpty();
    }

    @Test
    void recoveryFailureTouchMustPreserveTheClaimAndMoveItPastTheScannedCutoff() {
        Date preparedAt = at("2026-07-15T02:00:00Z");
        Date claimedAt = at("2026-07-15T02:01:00Z");
        Date staleBefore = at("2026-07-15T02:02:00Z");
        Date retryAt = at("2026-07-15T02:03:00Z");
        repository.createDraft(preparedAsset(preparedAt));
        assertThat(repository.claimUploadCompletion(ASSET_ID, OWNER_ID, 0L, claimedAt)).isTrue();

        assertThat(repository.recordUploadRecoveryFailure(
                ASSET_ID, 1L, staleBefore, "canonical lookup unavailable", retryAt)).isTrue();
        assertThat(repository.recordUploadRecoveryFailure(
                ASSET_ID, 1L, staleBefore, "must not touch twice", at("2026-07-15T02:04:00Z"))).isFalse();

        PostMediaAsset touched = repository.getRequired(ASSET_ID);
        assertThat(touched.uploadStatus()).isEqualTo(PostMediaUploadStatus.COMPLETING);
        assertThat(touched.uploadOperationVersion()).isEqualTo(1L);
        assertThat(touched.uploadUpdatedAt().toInstant()).isEqualTo(retryAt.toInstant());
        assertThat(touched.failureReason()).isEqualTo("canonical lookup unavailable");
        assertThat(repository.listStaleCompleting(staleBefore, 10)).isEmpty();
    }

    private PostMediaAsset preparedAsset(Date preparedAt) {
        return new PostMediaAsset(
                ASSET_ID,
                OWNER_ID,
                null,
                OBJECT_ID,
                VERSION_ID,
                null,
                UUID.fromString("00000000-0000-7000-8000-000000000707"),
                "demo.mp4",
                "video/mp4",
                1234L,
                PostMediaKind.VIDEO,
                PostMediaAssetLifecycle.DRAFT,
                PostVideoState.NONE,
                "",
                "",
                preparedAt,
                null
        );
    }

    private static Date at(String value) {
        return Date.from(Instant.parse(value));
    }
}
