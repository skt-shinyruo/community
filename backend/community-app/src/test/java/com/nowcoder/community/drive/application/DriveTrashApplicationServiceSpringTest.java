package com.nowcoder.community.drive.application;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.drive.application.port.DriveObjectStoragePort;
import com.nowcoder.community.drive.domain.model.DriveEntry;
import com.nowcoder.community.drive.domain.model.DriveEntryStatus;
import com.nowcoder.community.drive.domain.model.DriveSpace;
import com.nowcoder.community.drive.domain.repository.DriveEntryRepository;
import com.nowcoder.community.drive.domain.repository.DriveSpaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest(
        classes = CommunityAppApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@ActiveProfiles("test")
class DriveTrashApplicationServiceSpringTest {

    @Autowired
    private DriveTrashApplicationService service;

    @Autowired
    private DriveSpaceRepository spaceRepository;

    @Autowired
    private DriveEntryRepository entryRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private DriveObjectStoragePort objectStoragePort;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from drive_share_access");
        jdbcTemplate.update("delete from drive_share");
        jdbcTemplate.update("delete from drive_upload");
        jdbcTemplate.update("delete from drive_entry");
        jdbcTemplate.update("delete from drive_space");
    }

    @Test
    void deletePermanentlyShouldCommitDriveMutationBeforeOssFailureAndRetryCleanup() {
        UUID userId = uuid(7);
        Instant now = Instant.parse("2026-05-09T00:00:00Z");
        DriveSpace space = DriveSpace.createDefault(uuid(80), userId, now).reserve(8, now);
        spaceRepository.save(space);

        UUID objectId = uuid(81);
        UUID versionId = uuid(82);
        UUID fileId = uuid(83);
        DriveEntry trashedFile = DriveEntry.file(fileId, space.spaceId(), null, "a.txt", objectId, versionId, 8, "text/plain", now)
                .trash(now, now.plusSeconds(86_400));
        entryRepository.save(trashedFile);

        doThrow(new RuntimeException("oss down"))
                .doNothing()
                .when(objectStoragePort)
                .deleteObject(objectId, userId.toString());

        assertThatThrownBy(() -> service.deletePermanently(userId, fileId))
                .isInstanceOf(BusinessException.class)
                .hasMessage("网盘存储服务不可用");

        assertThat(entryRepository.findById(space.spaceId(), fileId).orElseThrow().status()).isEqualTo(DriveEntryStatus.DELETED);
        assertThat(spaceRepository.findById(space.spaceId()).orElseThrow().usedBytes()).isZero();

        service.deletePermanently(userId, fileId);

        verify(objectStoragePort, times(2)).deleteObject(objectId, userId.toString());
    }
}
