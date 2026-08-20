package com.nowcoder.community.drive.application;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.id.BinaryUuidCodec;
import com.nowcoder.community.common.web.net.ClientIpResolver;
import com.nowcoder.community.drive.application.DriveUploadApplicationService.CompleteUploadCommand;
import com.nowcoder.community.drive.application.command.DriveUploadContent;
import com.nowcoder.community.drive.application.DriveUploadApplicationService.PrepareUploadCommand;
import com.nowcoder.community.drive.application.port.DriveObjectStoragePort;
import com.nowcoder.community.drive.domain.model.DriveUpload;
import com.nowcoder.community.drive.domain.model.DriveUploadStatus;
import com.nowcoder.community.drive.domain.repository.DriveSpaceRepository;
import com.nowcoder.community.drive.domain.repository.DriveUploadRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(
        classes = CommunityAppApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@ActiveProfiles("test")
class DriveUploadApplicationServiceSpringTest {

    @Autowired
    private DriveUploadApplicationService service;

    @MockitoSpyBean
    private DriveSpaceRepository spaceRepository;

    @Autowired
    private DriveUploadRepository uploadRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private DriveObjectStoragePort objectStoragePort;

    @MockitoBean
    private ClientIpResolver clientIpResolver;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from drive_share_access");
        jdbcTemplate.update("delete from drive_share");
        jdbcTemplate.update("delete from drive_upload");
        jdbcTemplate.update("delete from drive_entry");
        jdbcTemplate.update("delete from drive_space");
    }

    @Test
    void prepareUploadShouldCallObjectStorageOutsideDatabaseTransaction() {
        UUID userId = uuid(6);
        Instant expiresAt = Instant.now().plusSeconds(900);
        when(objectStoragePort.prepareUpload(any())).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return new DriveObjectStoragePort.PreparedObject(uuid(91), uuid(92), uuid(93), expiresAt);
        });

        var session = service.prepareUpload(new PrepareUploadCommand(
                userId, null, "outside-transaction.txt", "text/plain", 1L, ""));

        DriveUpload persisted = uploadRepository.findById(UUID.fromString(session.uploadId())).orElseThrow();
        assertThat(persisted.status()).isEqualTo(DriveUploadStatus.PREPARED);
    }

    @Test
    void expiredUploadCompletionShouldPersistExpiredStatusInNewTransaction() {
        UUID userId = uuid(7);
        Instant expiredAt = Instant.now().minusSeconds(60);
        when(objectStoragePort.prepareUpload(any()))
                .thenReturn(new DriveObjectStoragePort.PreparedObject(
                        uuid(101),
                        uuid(102),
                        uuid(103),
                        expiredAt
                ));

        var session = service.prepareUpload(new PrepareUploadCommand(userId, null, "expired.txt", "text/plain", 1L, ""));

        assertThatThrownBy(() -> service.completeUpload(new CompleteUploadCommand(
                userId,
                UUID.fromString(session.uploadId()),
                new DriveUploadContent(() -> new ByteArrayInputStream("x".getBytes()), "text/plain", 1L)
        ))).isInstanceOf(RuntimeException.class)
                .hasMessage("上传会话不可用");

        DriveUpload persisted = uploadRepository.findById(UUID.fromString(session.uploadId())).orElseThrow();
        assertThat(persisted.status()).isEqualTo(DriveUploadStatus.EXPIRED);
        verify(objectStoragePort, times(0)).completeUpload(any());
    }

    @Test
    void concurrentLargeUploadCompletionShouldKeepQuotaReservationAtomic() {
        UUID userId = uuid(8);
        long uploadSize = 6_000_000_000L;
        Instant expiresAt = Instant.now().plusSeconds(3600);
        when(objectStoragePort.prepareUpload(any()))
                .thenReturn(
                        new DriveObjectStoragePort.PreparedObject(uuid(111), uuid(112), uuid(113), expiresAt),
                        new DriveObjectStoragePort.PreparedObject(uuid(121), uuid(122), uuid(123), expiresAt)
                );
        var firstSession = service.prepareUpload(new PrepareUploadCommand(userId, null, "first.bin", "application/octet-stream", uploadSize, ""));
        var secondSession = service.prepareUpload(new PrepareUploadCommand(userId, null, "second.bin", "application/octet-stream", uploadSize, ""));

        service.completeUpload(new CompleteUploadCommand(
                userId,
                UUID.fromString(firstSession.uploadId()),
                new DriveUploadContent(() -> new ByteArrayInputStream("first".getBytes()), "application/octet-stream", uploadSize)
        ));

        assertThatThrownBy(() -> service.completeUpload(new CompleteUploadCommand(
                userId,
                UUID.fromString(secondSession.uploadId()),
                new DriveUploadContent(() -> new ByteArrayInputStream("second".getBytes()), "application/octet-stream", uploadSize)
        ))).isInstanceOf(RuntimeException.class)
                .hasMessage("网盘容量不足");

        assertThat(spaceRepository.findByUserId(userId).orElseThrow().usedBytes()).isEqualTo(uploadSize);
        verify(objectStoragePort, times(1)).completeUpload(any());
    }

    @Test
    void terminalFinalizationFailureShouldRollbackEntryBeforeFailingUploadInNewTransaction() {
        UUID userId = uuid(9);
        Instant expiresAt = Instant.now().plusSeconds(900);
        when(objectStoragePort.prepareUpload(any()))
                .thenReturn(new DriveObjectStoragePort.PreparedObject(
                        uuid(131),
                        uuid(132),
                        uuid(133),
                        expiresAt
                ));
        var session = service.prepareUpload(new PrepareUploadCommand(
                userId, null, "rollback.txt", "text/plain", 2L, ""));
        UUID uploadId = UUID.fromString(session.uploadId());
        doAnswer(invocation -> {
            DriveUpload completing = uploadRepository.findById(uploadId).orElseThrow();
            assertThat(completing.status()).isEqualTo(DriveUploadStatus.COMPLETING);
            jdbcTemplate.update(
                    "update drive_space set reserved_bytes = 1 where space_id = ?",
                    BinaryUuidCodec.toBytes(completing.spaceId())
            );
            return null;
        }).when(objectStoragePort).completeUpload(any());

        assertThatThrownBy(() -> service.completeUpload(new CompleteUploadCommand(
                userId,
                uploadId,
                new DriveUploadContent(() -> new ByteArrayInputStream("xx".getBytes()), "text/plain", 2L)
        ))).isInstanceOf(RuntimeException.class)
                .hasMessage("网盘容量不足");

        DriveUpload failed = uploadRepository.findById(uploadId).orElseThrow();
        assertThat(failed.status()).isEqualTo(DriveUploadStatus.FAILED);
        assertThat(spaceRepository.findByUserId(userId).orElseThrow().reservedBytes()).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from drive_entry where entry_id = ?",
                Integer.class,
                BinaryUuidCodec.toBytes(failed.completedEntryId())
        )).isZero();
        verify(objectStoragePort).deleteObject(failed.objectId(), userId.toString());
    }

    @Test
    void concurrentRecoveryShouldReleaseCompletingUploadReservationOnce() throws Exception {
        UUID userId = uuid(10);
        long uploadSize = 512L;
        Instant expiresAt = Instant.now().plusSeconds(900);
        when(objectStoragePort.prepareUpload(any()))
                .thenReturn(new DriveObjectStoragePort.PreparedObject(
                        uuid(141),
                        uuid(142),
                        uuid(143),
                        expiresAt
                ));
        var session = service.prepareUpload(new PrepareUploadCommand(
                userId, null, "concurrent-recovery.bin", "application/octet-stream", uploadSize, ""));
        UUID uploadId = UUID.fromString(session.uploadId());
        DriveUpload prepared = uploadRepository.findById(uploadId).orElseThrow();
        Instant staleAt = Instant.now().minusSeconds(3_601);
        assertThat(spaceRepository.reserve(prepared.spaceId(), uploadSize, staleAt)).isTrue();
        assertThat(uploadRepository.transitionStatus(
                prepared.startCompleting(uuid(144), staleAt),
                DriveUploadStatus.PREPARED
        )).isTrue();

        when(objectStoragePort.getMetadata(prepared.objectId())).thenReturn(new DriveObjectStoragePort.ObjectMetadata(
                prepared.objectId(),
                prepared.versionId(),
                "STAGED"
        ));
        CountDownLatch bothAtCancellation = new CountDownLatch(2);
        CountDownLatch allowCancellation = new CountDownLatch(1);
        when(objectStoragePort.cancelUpload(prepared.ossSessionId(), prepared.objectId(), prepared.versionId()))
                .thenAnswer(invocation -> {
                    bothAtCancellation.countDown();
                    await(allowCancellation);
                    return new DriveObjectStoragePort.UploadCancellation(false, true);
                });
        clearInvocations(spaceRepository, objectStoragePort);

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Instant updatedBefore = Instant.now();
            Future<?> first = executor.submit(() -> {
                await(start);
                return service.recoverStaleUploads(updatedBefore, 10);
            });
            Future<?> second = executor.submit(() -> {
                await(start);
                return service.recoverStaleUploads(updatedBefore, 10);
            });

            start.countDown();
            assertThat(bothAtCancellation.await(5, TimeUnit.SECONDS)).isTrue();
            allowCancellation.countDown();
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);

            assertThat(uploadRepository.findById(uploadId).orElseThrow().status())
                    .isEqualTo(DriveUploadStatus.FAILED);
            assertThat(spaceRepository.findById(prepared.spaceId()).orElseThrow().reservedBytes()).isZero();
            verify(objectStoragePort, times(2))
                    .cancelUpload(prepared.ossSessionId(), prepared.objectId(), prepared.versionId());
            verify(spaceRepository, times(1))
                    .releaseReserved(eq(prepared.spaceId()), eq(uploadSize), any(Instant.class));
            verify(objectStoragePort, atLeastOnce()).deleteObject(prepared.objectId(), userId.toString());
        } finally {
            allowCancellation.countDown();
            executor.shutdownNow();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted while awaiting concurrent recovery", e);
        }
    }
}
