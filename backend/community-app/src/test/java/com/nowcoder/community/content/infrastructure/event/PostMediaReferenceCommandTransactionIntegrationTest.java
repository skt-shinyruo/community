package com.nowcoder.community.content.infrastructure.event;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.id.BinaryUuidCodec;
import com.nowcoder.community.common.web.net.ClientIpResolver;
import com.nowcoder.community.content.application.PostMediaReferenceCommandPublisher;
import com.nowcoder.community.content.application.command.PostMediaReferenceCommand;
import com.nowcoder.community.content.domain.model.PostMediaReferenceOperation;
import com.nowcoder.community.content.domain.model.PostVideoState;
import com.nowcoder.community.content.domain.repository.PostMediaAssetRepository;
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
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(
        classes = CommunityAppApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "content.media.reference-command-topic=command.content.post-media-reference"
)
@ActiveProfiles("test")
class PostMediaReferenceCommandTransactionIntegrationTest {

    private static final String TOPIC = "command.content.post-media-reference";
    private static final UUID ASSET_ID = UUID.fromString("00000000-0000-7000-8000-000000005001");
    private static final UUID OWNER_ID = UUID.fromString("00000000-0000-7000-8000-000000005002");
    private static final UUID POST_ID = UUID.fromString("00000000-0000-7000-8000-000000005003");
    private static final UUID OBJECT_ID = UUID.fromString("00000000-0000-7000-8000-000000005004");
    private static final UUID VERSION_ID = UUID.fromString("00000000-0000-7000-8000-000000005005");
    private static final UUID REFERENCE_ID = UUID.fromString("00000000-0000-7000-8000-000000005006");
    private static final Date NOW = Date.from(Instant.parse("2026-07-15T05:00:00Z"));

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private PostMediaAssetRepository repository;

    @Autowired
    private PostMediaReferenceCommandPublisher publisher;

    @MockBean
    private ClientIpResolver clientIpResolver;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from outbox_event");
        jdbcTemplate.update("delete from post_media_asset");
        insertUploadedAsset();
    }

    @Test
    void desiredStateAndCommandShouldCommitTogether() {
        transactionTemplate.executeWithoutResult(status -> {
            long operationVersion = repository.requestBind(
                    ASSET_ID,
                    POST_ID,
                    REFERENCE_ID,
                    PostVideoState.NONE,
                    NOW
            );
            publisher.publish(new PostMediaReferenceCommand(
                    ASSET_ID,
                    PostMediaReferenceOperation.BIND,
                    operationVersion,
                    OWNER_ID
            ));

            assertThat(referenceStatus()).isEqualTo("BIND_PENDING");
            assertThat(outboxCount()).isOne();
        });

        assertThat(referenceStatus()).isEqualTo("BIND_PENDING");
        assertThat(referenceOperationVersion()).isEqualTo(1L);
        assertThat(outboxCount()).isOne();
        assertThat(singleOutboxValue("event_id")).isEqualTo(
                "content-media-reference:" + ASSET_ID + ":1:BIND"
        );
        assertThat(singleOutboxValue("topic")).isEqualTo(TOPIC);
        assertThat(singleOutboxValue("status")).isEqualTo("PENDING");
    }

    @Test
    void desiredStateAndCommandShouldRollbackTogether() {
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            long operationVersion = repository.requestBind(
                    ASSET_ID,
                    POST_ID,
                    REFERENCE_ID,
                    PostVideoState.NONE,
                    NOW
            );
            publisher.publish(new PostMediaReferenceCommand(
                    ASSET_ID,
                    PostMediaReferenceOperation.BIND,
                    operationVersion,
                    OWNER_ID
            ));

            assertThat(referenceStatus()).isEqualTo("BIND_PENDING");
            assertThat(outboxCount()).isOne();
            throw new IllegalStateException("force main transaction rollback");
        }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("force main transaction rollback");

        assertThat(referenceStatus()).isEqualTo("UNBOUND");
        assertThat(referenceOperationVersion()).isZero();
        assertThat(outboxCount()).isZero();
    }

    private void insertUploadedAsset() {
        jdbcTemplate.update(
                "insert into post_media_asset(" +
                        "id, owner_user_id, oss_object_id, oss_version_id, file_name, content_type, content_length, " +
                        "media_kind, lifecycle, video_state, public_url, failure_reason, reference_status, " +
                        "reference_operation_version, reference_updated_at, create_time" +
                        ") values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                BinaryUuidCodec.toBytes(ASSET_ID),
                BinaryUuidCodec.toBytes(OWNER_ID),
                BinaryUuidCodec.toBytes(OBJECT_ID),
                BinaryUuidCodec.toBytes(VERSION_ID),
                "cover.png",
                "image/png",
                256L,
                "IMAGE",
                "UPLOADED",
                "NONE",
                "https://cdn.example.com/cover.png",
                "",
                "UNBOUND",
                0L,
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

    private long referenceOperationVersion() {
        Long value = jdbcTemplate.queryForObject(
                "select reference_operation_version from post_media_asset where id = ?",
                Long.class,
                BinaryUuidCodec.toBytes(ASSET_ID)
        );
        return value == null ? -1L : value;
    }

    private long outboxCount() {
        Long value = jdbcTemplate.queryForObject("select count(*) from outbox_event", Long.class);
        return value == null ? 0L : value;
    }

    private String singleOutboxValue(String column) {
        return jdbcTemplate.queryForObject(
                "select " + column + " from outbox_event",
                String.class
        );
    }
}
