package com.nowcoder.community.content.infrastructure.persistence;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.id.BinaryUuidCodec;
import com.nowcoder.community.common.web.net.ClientIpResolver;
import com.nowcoder.community.content.domain.model.PostMediaAsset;
import com.nowcoder.community.content.domain.model.PostMediaReferenceStatus;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = CommunityAppApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class PostMediaReferenceReconciliationScanPersistenceTest {

    private static final UUID FIRST = UUID.fromString("00000000-0000-7000-8000-000000006301");
    private static final UUID SECOND = UUID.fromString("00000000-0000-7000-8000-000000006302");
    private static final UUID THIRD = UUID.fromString("00000000-0000-7000-8000-000000006303");
    private static final UUID OWNER = UUID.fromString("00000000-0000-7000-8000-000000006304");
    private static final Instant NOW = Instant.parse("2026-07-15T12:00:00Z");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PostMediaAssetRepository repository;

    @MockBean
    private ClientIpResolver clientIpResolver;

    @BeforeEach
    void setUp() {
        for (UUID id : List.of(FIRST, SECOND, THIRD)) {
            jdbcTemplate.update("delete from post_media_asset where id = ?", bytes(id));
        }
        insert(FIRST, "BIND_PENDING", "UPLOADED", 1L);
        insert(SECOND, "BOUND", "BOUND", 2L);
        insert(THIRD, "RELEASED", "RELEASED", 3L);
    }

    @Test
    void scanShouldUseStableAssetIdCursorAndIncludePendingBoundAndReleasedStates() {
        List<PostMediaAsset> page = repository.scanReferenceStatesAfter(FIRST, 2);

        assertThat(page).extracting(PostMediaAsset::id).containsExactly(SECOND, THIRD);
        assertThat(page).extracting(PostMediaAsset::referenceStatus)
                .containsExactly(PostMediaReferenceStatus.BOUND, PostMediaReferenceStatus.RELEASED);
    }

    private void insert(UUID id, String referenceStatus, String lifecycle, long version) {
        jdbcTemplate.update(
                """
                        insert into post_media_asset(
                            id, owner_user_id, oss_object_id, oss_version_id, oss_reference_id,
                            file_name, content_type, content_length, media_kind, lifecycle,
                            reference_status, reference_operation_version, reference_updated_at,
                            video_state, public_url, failure_reason, create_time, update_time
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                bytes(id),
                bytes(OWNER),
                bytes(new UUID(id.getMostSignificantBits(), id.getLeastSignificantBits() + 100L)),
                bytes(new UUID(id.getMostSignificantBits(), id.getLeastSignificantBits() + 200L)),
                bytes(new UUID(id.getMostSignificantBits(), id.getLeastSignificantBits() + 300L)),
                "fixture.bin",
                "application/octet-stream",
                128L,
                "FILE",
                lifecycle,
                referenceStatus,
                version,
                Timestamp.from(NOW),
                "NONE",
                "https://cdn.example.com/fixture.bin",
                "",
                Timestamp.from(NOW),
                Timestamp.from(NOW)
        );
    }

    private byte[] bytes(UUID value) {
        return BinaryUuidCodec.toBytes(value);
    }
}
