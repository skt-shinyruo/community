package com.nowcoder.community.content.infrastructure.event;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.id.BinaryUuidCodec;
import com.nowcoder.community.common.web.net.ClientIpResolver;
import com.nowcoder.community.content.domain.event.PostDomainEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(
        classes = CommunityAppApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "content.media.reference-command-topic=command.content.post-media-reference"
)
@ActiveProfiles("test")
class PostDeletionMediaReferenceTransactionIntegrationTest {

    private static final UUID POST_ID = UUID.fromString("00000000-0000-7000-8000-000000006101");
    private static final UUID AUTHOR_ID = UUID.fromString("00000000-0000-7000-8000-000000006102");
    private static final UUID CATEGORY_ID = UUID.fromString("00000000-0000-7000-8000-000000006103");
    private static final UUID ASSET_ID = UUID.fromString("00000000-0000-7000-8000-000000006104");
    private static final UUID OBJECT_ID = UUID.fromString("00000000-0000-7000-8000-000000006105");
    private static final UUID VERSION_ID = UUID.fromString("00000000-0000-7000-8000-000000006106");
    private static final UUID REFERENCE_ID = UUID.fromString("00000000-0000-7000-8000-000000006107");
    private static final Instant NOW = Instant.parse("2026-07-15T10:00:00Z");
    private static final String POST_EVENT_ID = "content:PostDeleted:" + POST_ID;
    private static final String MEDIA_EVENT_ID =
            "content-media-reference:" + ASSET_ID + ":6:RELEASE";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private PostDomainEventPublisher postDomainEventPublisher;

    @MockBean
    private ClientIpResolver clientIpResolver;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from outbox_event where event_id in (?, ?)", POST_EVENT_ID, MEDIA_EVENT_ID);
        jdbcTemplate.update("delete from post_content_block where post_id = ?", bytes(POST_ID));
        jdbcTemplate.update("delete from post_media_asset where id = ?", bytes(ASSET_ID));
        jdbcTemplate.update("delete from discuss_post where id = ?", bytes(POST_ID));
        insertPost();
        insertBoundAsset();
    }

    @Test
    void committedPostDeletionShouldCommitReleaseIntentAndBothDurableEventsTogether() {
        transactionTemplate.executeWithoutResult(status -> {
            markPostDeleted();
            postDomainEventPublisher.postDeleted(POST_ID);
        });

        assertThat(postStatus()).isEqualTo(2);
        assertThat(referenceStatus()).isEqualTo("RELEASE_PENDING");
        assertThat(referenceOperationVersion()).isEqualTo(6L);
        assertThat(outboxEventIds()).containsExactlyInAnyOrder(POST_EVENT_ID, MEDIA_EVENT_ID);
    }

    @Test
    void deletionWhileBindIsPendingShouldSupersedeTheStaleBindCommandWithRelease() {
        markAssetBindPending();

        transactionTemplate.executeWithoutResult(status -> {
            markPostDeleted();
            postDomainEventPublisher.postDeleted(POST_ID);
        });

        assertThat(referenceStatus()).isEqualTo("RELEASE_PENDING");
        assertThat(referenceOperationVersion()).isEqualTo(6L);
        assertThat(outboxEventIds()).containsExactlyInAnyOrder(POST_EVENT_ID, MEDIA_EVENT_ID);
    }

    @Test
    void rolledBackPostDeletionShouldLeaveBoundReferenceAndNoDurableEvents() {
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            markPostDeleted();
            postDomainEventPublisher.postDeleted(POST_ID);
            throw new IllegalStateException("force post deletion rollback");
        }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("force post deletion rollback");

        assertThat(postStatus()).isZero();
        assertThat(referenceStatus()).isEqualTo("BOUND");
        assertThat(referenceOperationVersion()).isEqualTo(5L);
        assertThat(outboxEventIds()).isEmpty();
    }

    private void insertPost() {
        jdbcTemplate.update(
                """
                        insert into discuss_post(
                            id, user_id, category_id, title, type, status,
                            create_time, update_time, comment_count, score
                        ) values (?, ?, ?, ?, 0, 0, ?, ?, 0, 0)
                        """,
                bytes(POST_ID),
                bytes(AUTHOR_ID),
                bytes(CATEGORY_ID),
                "media deletion transaction fixture",
                Timestamp.from(NOW),
                Timestamp.from(NOW)
        );
    }

    private void insertBoundAsset() {
        jdbcTemplate.update(
                """
                        insert into post_media_asset(
                            id, owner_user_id, post_id, oss_object_id, oss_version_id, oss_reference_id,
                            file_name, content_type, content_length, media_kind, lifecycle,
                            reference_status, reference_operation_version, reference_updated_at,
                            video_state, public_url, failure_reason, create_time, update_time
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                bytes(ASSET_ID),
                bytes(AUTHOR_ID),
                bytes(POST_ID),
                bytes(OBJECT_ID),
                bytes(VERSION_ID),
                bytes(REFERENCE_ID),
                "fixture.png",
                "image/png",
                256L,
                "IMAGE",
                "BOUND",
                "BOUND",
                5L,
                Timestamp.from(NOW),
                "NONE",
                "https://cdn.example.com/fixture.png",
                "",
                Timestamp.from(NOW),
                Timestamp.from(NOW)
        );
    }

    private void markPostDeleted() {
        jdbcTemplate.update(
                "update discuss_post set status = 2, update_time = ? where id = ? and status = 0",
                Timestamp.from(NOW.plusSeconds(60)),
                bytes(POST_ID)
        );
    }

    private void markAssetBindPending() {
        jdbcTemplate.update(
                """
                        update post_media_asset
                        set lifecycle = 'UPLOADED', reference_status = 'BIND_PENDING'
                        where id = ?
                        """,
                bytes(ASSET_ID)
        );
    }

    private int postStatus() {
        Integer value = jdbcTemplate.queryForObject(
                "select status from discuss_post where id = ?",
                Integer.class,
                bytes(POST_ID)
        );
        return value == null ? -1 : value;
    }

    private String referenceStatus() {
        return jdbcTemplate.queryForObject(
                "select reference_status from post_media_asset where id = ?",
                String.class,
                bytes(ASSET_ID)
        );
    }

    private long referenceOperationVersion() {
        Long value = jdbcTemplate.queryForObject(
                "select reference_operation_version from post_media_asset where id = ?",
                Long.class,
                bytes(ASSET_ID)
        );
        return value == null ? -1L : value;
    }

    private List<String> outboxEventIds() {
        return jdbcTemplate.queryForList(
                "select event_id from outbox_event where event_id in (?, ?) order by event_id",
                String.class,
                POST_EVENT_ID,
                MEDIA_EVENT_ID
        );
    }

    private byte[] bytes(UUID value) {
        return BinaryUuidCodec.toBytes(value);
    }
}
