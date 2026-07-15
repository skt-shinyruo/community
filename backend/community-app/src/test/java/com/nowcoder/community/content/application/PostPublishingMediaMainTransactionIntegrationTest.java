package com.nowcoder.community.content.application;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.id.BinaryUuidCodec;
import com.nowcoder.community.common.idempotency.IdempotencyGuard;
import com.nowcoder.community.common.web.net.ClientIpResolver;
import com.nowcoder.community.content.application.command.CreatePostCommand;
import com.nowcoder.community.content.application.command.PostContentBlockCommand;
import com.nowcoder.community.content.application.command.PostMediaReferenceCommand;
import com.nowcoder.community.content.application.result.PostCreateResult;
import com.nowcoder.community.content.domain.event.PostDomainEventPublisher;
import com.nowcoder.community.content.domain.model.PostDraft;
import com.nowcoder.community.content.domain.model.PostMediaReferenceOperation;
import com.nowcoder.community.content.domain.repository.CategoryRepository;
import com.nowcoder.community.content.domain.repository.PostContentBlockRepository;
import com.nowcoder.community.content.domain.repository.PostRepository;
import com.nowcoder.community.content.domain.repository.PostTagRepository;
import com.nowcoder.community.content.domain.service.PostContentBlockPolicy;
import com.nowcoder.community.content.domain.service.PostPublishingDomainService;
import com.nowcoder.community.content.exception.ContentErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@SpringBootTest(
        classes = CommunityAppApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "content.media.reference-command-topic=command.content.post-media-reference"
)
@ActiveProfiles("test")
class PostPublishingMediaMainTransactionIntegrationTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-7000-8000-000000006201");
    private static final UUID CATEGORY_ID = UUID.fromString("00000000-0000-7000-8000-000000006202");
    private static final UUID POST_ID = UUID.fromString("00000000-0000-7000-8000-000000006203");
    private static final UUID ASSET_ID = UUID.fromString("00000000-0000-7000-8000-000000006204");
    private static final UUID OBJECT_ID = UUID.fromString("00000000-0000-7000-8000-000000006205");
    private static final UUID VERSION_ID = UUID.fromString("00000000-0000-7000-8000-000000006206");
    private static final UUID LEGACY_REFERENCE_ID = UUID.fromString("00000000-0000-7000-8000-000000006207");
    private static final Instant NOW = Instant.parse("2026-07-15T10:30:00Z");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PostPublishingApplicationService applicationService;

    @MockBean
    private ContentSanitizer sanitizer;

    @MockBean
    private IdempotencyGuard idempotencyGuard;

    @MockBean
    private UserModerationGuard moderationGuard;

    @MockBean
    private PostPublishingDomainService domainService;

    @MockBean
    private PostContentBlockPolicy blockPolicy;

    @MockBean
    private PostRepository postRepository;

    @MockBean
    private PostContentBlockRepository blockRepository;

    @MockBean
    private CategoryRepository categoryRepository;

    @MockBean
    private PostTagRepository tagRepository;

    @MockBean
    private PostDomainEventPublisher domainEventPublisher;

    @MockBean
    private PostMediaStoragePort storagePort;

    @MockBean
    private ClientIpResolver clientIpResolver;

    @SpyBean
    private PostMediaReferenceCommandPublisher commandPublisher;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from outbox_event where event_key = ?", ASSET_ID.toString());
        jdbcTemplate.update("delete from post_media_asset where id = ?", bytes(ASSET_ID));
        insertUploadedAsset();
        when(sanitizer.filter(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        when(idempotencyGuard.executeRequired(
                eq("content:create_post"),
                eq(USER_ID),
                anyString(),
                anyString(),
                eq(ContentErrorCode.REQUEST_REPLAY_CONFLICT),
                eq(PostCreateResult.class),
                any()
        )).thenAnswer(invocation -> invocation.<Supplier<PostCreateResult>>getArgument(6).get());
        PostDraft draft = new PostDraft(USER_ID, "title", CATEGORY_ID, Timestamp.from(NOW));
        when(domainService.createDraft(USER_ID, "title", CATEGORY_ID)).thenReturn(draft);
        when(postRepository.create(draft)).thenReturn(POST_ID);
        List<PostContentBlockCommand> blocks = blocks();
        when(blockPolicy.validateAndNormalize(blocks)).thenReturn(blocks);
        when(storagePort.bindReference(any(), eq(POST_ID), eq(USER_ID))).thenReturn(LEGACY_REFERENCE_ID);
        doThrow(new IllegalStateException("fail after media intent"))
                .when(blockRepository).replaceBlocks(eq(POST_ID), any());
    }

    @Test
    void createFailureAfterMediaSchedulingShouldRollbackIntentAndOutboxWithoutRemoteCompensation() {
        assertThatThrownBy(() -> applicationService.create(
                "media-main-tx-rollback",
                new CreatePostCommand(USER_ID, "title", CATEGORY_ID, List.of(), blocks())
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("fail after media intent");

        verify(commandPublisher).publish(argThat(command -> command != null
                && ASSET_ID.equals(command.assetId())
                && command.operation() == PostMediaReferenceOperation.BIND
                && USER_ID.equals(command.actorUserId())));
        assertThat(referenceStatus()).isEqualTo("UNBOUND");
        assertThat(referenceOperationVersion()).isZero();
        assertThat(mediaOutboxCount()).isZero();
        verifyNoInteractions(storagePort);
    }

    @Test
    void updateFailureAfterReleaseSchedulingShouldRollbackIntentAndOutbox() {
        markAssetBound();
        when(postRepository.getRequiredSnapshot(POST_ID))
                .thenReturn(new com.nowcoder.community.content.domain.model.PostSnapshot(
                        POST_ID,
                        USER_ID,
                        0,
                        Timestamp.from(NOW)
                ));
        when(blockPolicy.validateAndNormalize(List.<PostContentBlockCommand>of())).thenReturn(List.of());
        doNothing().when(blockRepository).replaceBlocks(eq(POST_ID), any());
        doThrow(new IllegalStateException("fail after release intent"))
                .when(tagRepository).replaceTagsForPost(POST_ID, List.of());

        assertThatThrownBy(() -> applicationService.updatePost(
                USER_ID,
                POST_ID,
                "title",
                CATEGORY_ID,
                List.of(),
                List.of()
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("fail after release intent");

        verify(commandPublisher).publish(new PostMediaReferenceCommand(
                ASSET_ID,
                PostMediaReferenceOperation.RELEASE,
                6L,
                USER_ID
        ));
        assertThat(referenceStatus()).isEqualTo("BOUND");
        assertThat(referenceOperationVersion()).isEqualTo(5L);
        assertThat(mediaOutboxCount()).isZero();
    }

    private List<PostContentBlockCommand> blocks() {
        return List.of(new PostContentBlockCommand("image", "", ASSET_ID, "", "", "", null));
    }

    private void insertUploadedAsset() {
        jdbcTemplate.update(
                """
                        insert into post_media_asset(
                            id, owner_user_id, oss_object_id, oss_version_id,
                            file_name, content_type, content_length, media_kind, lifecycle,
                            reference_status, reference_operation_version, reference_updated_at,
                            video_state, public_url, failure_reason, create_time, update_time
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                bytes(ASSET_ID),
                bytes(USER_ID),
                bytes(OBJECT_ID),
                bytes(VERSION_ID),
                "fixture.png",
                "image/png",
                256L,
                "IMAGE",
                "UPLOADED",
                "UNBOUND",
                0L,
                Timestamp.from(NOW),
                "NONE",
                "https://cdn.example.com/fixture.png",
                "",
                Timestamp.from(NOW),
                Timestamp.from(NOW)
        );
    }

    private void markAssetBound() {
        jdbcTemplate.update(
                """
                        update post_media_asset
                        set post_id = ?, oss_reference_id = ?, lifecycle = 'BOUND',
                            reference_status = 'BOUND', reference_operation_version = 5
                        where id = ?
                        """,
                bytes(POST_ID),
                bytes(LEGACY_REFERENCE_ID),
                bytes(ASSET_ID)
        );
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

    private long mediaOutboxCount() {
        Long value = jdbcTemplate.queryForObject(
                "select count(*) from outbox_event where event_key = ?",
                Long.class,
                ASSET_ID.toString()
        );
        return value == null ? 0L : value;
    }

    private byte[] bytes(UUID value) {
        return BinaryUuidCodec.toBytes(value);
    }
}
