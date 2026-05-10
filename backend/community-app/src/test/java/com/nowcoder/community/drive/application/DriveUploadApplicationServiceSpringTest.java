package com.nowcoder.community.drive.application;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.web.net.ClientIpResolver;
import com.nowcoder.community.drive.application.command.CompleteDriveUploadCommand;
import com.nowcoder.community.drive.application.command.DriveUploadContent;
import com.nowcoder.community.drive.application.command.PrepareDriveUploadCommand;
import com.nowcoder.community.drive.application.port.DriveObjectStoragePort;
import com.nowcoder.community.drive.domain.model.DriveUpload;
import com.nowcoder.community.drive.domain.model.DriveUploadStatus;
import com.nowcoder.community.drive.domain.repository.DriveSpaceRepository;
import com.nowcoder.community.drive.domain.repository.DriveUploadRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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

    @Autowired
    private DriveSpaceRepository spaceRepository;

    @Autowired
    private DriveUploadRepository uploadRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private DriveObjectStoragePort objectStoragePort;

    @MockBean
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

        var session = service.prepareUpload(new PrepareDriveUploadCommand(userId, null, "expired.txt", "text/plain", 1L, ""));

        assertThatThrownBy(() -> service.completeUpload(new CompleteDriveUploadCommand(
                userId,
                UUID.fromString(session.uploadId()),
                new DriveUploadContent(() -> new ByteArrayInputStream("x".getBytes()), "text/plain", 1L, "")
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
        when(objectStoragePort.completeUpload(any())).thenAnswer(invocation -> {
            DriveObjectStoragePort.CompleteObject command = invocation.getArgument(0);
            return new DriveObjectStoragePort.StoredObject(command.objectId(), command.versionId(), "");
        });

        var firstSession = service.prepareUpload(new PrepareDriveUploadCommand(userId, null, "first.bin", "application/octet-stream", uploadSize, ""));
        var secondSession = service.prepareUpload(new PrepareDriveUploadCommand(userId, null, "second.bin", "application/octet-stream", uploadSize, ""));

        service.completeUpload(new CompleteDriveUploadCommand(
                userId,
                UUID.fromString(firstSession.uploadId()),
                new DriveUploadContent(() -> new ByteArrayInputStream("first".getBytes()), "application/octet-stream", uploadSize, "")
        ));

        assertThatThrownBy(() -> service.completeUpload(new CompleteDriveUploadCommand(
                userId,
                UUID.fromString(secondSession.uploadId()),
                new DriveUploadContent(() -> new ByteArrayInputStream("second".getBytes()), "application/octet-stream", uploadSize, "")
        ))).isInstanceOf(RuntimeException.class)
                .hasMessage("网盘容量不足");

        assertThat(spaceRepository.findByUserId(userId).orElseThrow().usedBytes()).isEqualTo(uploadSize);
        verify(objectStoragePort, times(1)).completeUpload(any());
    }
}
