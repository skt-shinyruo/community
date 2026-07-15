package com.nowcoder.community.content.application;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.content.application.command.PreparePostMediaUploadCommand;
import com.nowcoder.community.content.application.result.PostMediaUploadSessionResult;
import com.nowcoder.community.content.domain.model.PostMediaAsset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = CommunityAppApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class PostMediaUploadTransactionBoundaryTest {

    private static final UUID ACTOR_ID = uuid(7101);
    private static final UUID SESSION_ID = uuid(7102);
    private static final UUID OBJECT_ID = uuid(7103);
    private static final UUID VERSION_ID = uuid(7104);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PostMediaApplicationService service;

    @MockBean
    private PostMediaStoragePort storage;

    @BeforeEach
    void cleanMediaRows() {
        jdbcTemplate.update("delete from post_media_asset");
    }

    @Test
    void prepareRemoteCallMustRunOutsideContentDatabaseTransaction() {
        AtomicBoolean transactionActiveAtRemoteCall = new AtomicBoolean(true);
        when(storage.prepareUpload(any(), any())).thenAnswer(invocation -> {
            transactionActiveAtRemoteCall.set(
                    TransactionSynchronizationManager.isActualTransactionActive());
            PostMediaAsset draft = invocation.getArgument(0);
            return session(draft.id());
        });

        service.prepareUpload(command());

        assertThat(transactionActiveAtRemoteCall)
                .as("OSS prepare must not share the Content database transaction")
                .isFalse();
    }

    @Test
    void completeRemoteCallMustRunOutsideContentDatabaseTransaction() {
        when(storage.prepareUpload(any(), any())).thenAnswer(invocation ->
                session(invocation.getArgument(0, PostMediaAsset.class).id()));
        PostMediaUploadSessionResult prepared = service.prepareUpload(command());
        reset(storage);
        AtomicBoolean transactionActiveAtRemoteCall = new AtomicBoolean(true);
        when(storage.completeUpload(any(), any(), any())).thenAnswer(invocation -> {
            transactionActiveAtRemoteCall.set(
                    TransactionSynchronizationManager.isActualTransactionActive());
            return new PostMediaStoragePort.UploadedPostMedia(
                    VERSION_ID,
                    "https://cdn.example.test/post.png",
                    "image/png",
                    4L
            );
        });

        service.completeUpload(
                ACTOR_ID,
                prepared.assetId(),
                SESSION_ID,
                new PostMediaUploadContent(
                        () -> new ByteArrayInputStream(new byte[]{1, 2, 3, 4}),
                        "image/png",
                        4L,
                        "sha256-post"
                )
        );

        assertThat(transactionActiveAtRemoteCall)
                .as("OSS complete must run between short Content claim and finalize transactions")
                .isFalse();
    }

    private static PreparePostMediaUploadCommand command() {
        return new PreparePostMediaUploadCommand(
                ACTOR_ID,
                "post.png",
                "image/png",
                4L,
                "IMAGE",
                "sha256-post"
        );
    }

    private static PostMediaUploadSessionResult session(UUID assetId) {
        return new PostMediaUploadSessionResult(
                assetId,
                SESSION_ID.toString(),
                "/api/posts/media/" + assetId + "/upload",
                "POST",
                "file",
                "uploadId",
                100L,
                "image/png",
                Instant.parse("2026-07-15T00:15:00Z"),
                OBJECT_ID,
                VERSION_ID
        );
    }
}
