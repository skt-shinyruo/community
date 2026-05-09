package com.nowcoder.community.drive.infrastructure.oss;

import com.nowcoder.community.drive.application.command.DriveUploadContent;
import com.nowcoder.community.drive.application.port.DriveObjectStoragePort;
import com.nowcoder.community.oss.client.CommunityOssClient;
import com.nowcoder.community.oss.client.model.OssCompleteUploadRequest;
import com.nowcoder.community.oss.client.model.OssMetadataResponse;
import com.nowcoder.community.oss.client.model.OssUploadSessionRequest;
import com.nowcoder.community.oss.client.model.OssUploadSessionResponse;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OssDriveObjectStorageAdapterTest {

    @Test
    void adapterShouldMapDrivePortToCommunityOssClient() {
        CommunityOssClient client = mock(CommunityOssClient.class);
        UUID sessionId = uuid(1);
        UUID objectId = uuid(2);
        UUID versionId = uuid(3);
        when(client.prepareUpload(any())).thenReturn(new OssUploadSessionResponse(
                sessionId,
                objectId,
                versionId,
                "PROXY",
                "/api/oss/objects/" + objectId + "/complete",
                Instant.parse("2026-05-09T00:15:00Z")
        ));
        when(client.completeProxyUpload(any())).thenReturn(new OssMetadataResponse(
                objectId,
                versionId,
                "DRIVE_FILE",
                "ACTIVE",
                "application/pdf",
                4,
                "http://localhost:12880/files/" + objectId + "/" + versionId + "/report.pdf"
        ));

        OssDriveObjectStorageAdapter adapter = new OssDriveObjectStorageAdapter(client);
        DriveObjectStoragePort.PreparedObject prepared = adapter.prepareUpload(new DriveObjectStoragePort.PrepareObject(
                "DRIVE_FILE",
                "community-app",
                "drive",
                "drive-upload",
                uuid(7).toString(),
                "PRIVATE",
                "report.pdf",
                "application/pdf",
                4,
                "",
                uuid(7).toString()
        ));
        DriveObjectStoragePort.StoredObject stored = adapter.completeUpload(new DriveObjectStoragePort.CompleteObject(
                sessionId,
                objectId,
                versionId,
                "report.pdf",
                "application/pdf",
                4,
                "",
                new DriveUploadContent(() -> new ByteArrayInputStream("file".getBytes(StandardCharsets.UTF_8)), "application/pdf", 4, "")
        ));

        assertThat(prepared.objectId()).isEqualTo(objectId);
        assertThat(prepared.versionId()).isEqualTo(versionId);
        assertThat(stored.objectId()).isEqualTo(objectId);
        assertThat(stored.versionId()).isEqualTo(versionId);
        assertThat(stored.publicUrl()).contains("/files/");
        verify(client).prepareUpload(any(OssUploadSessionRequest.class));
        verify(client).completeProxyUpload(any(OssCompleteUploadRequest.class));
    }
}
