package com.nowcoder.community.drive.application;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.drive.application.port.DriveObjectStoragePort;
import com.nowcoder.community.drive.domain.model.DriveEntry;
import com.nowcoder.community.drive.domain.model.DriveEntryStatus;
import com.nowcoder.community.drive.domain.model.DriveSpace;
import com.nowcoder.community.drive.domain.repository.DriveEntryRepository;
import com.nowcoder.community.drive.domain.repository.DriveSpaceRepository;
import com.nowcoder.community.drive.exception.DriveErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockingDetails;
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

    @SpyBean
    private DriveSpaceRepository spaceRepository;

    @Autowired
    private DriveEntryRepository entryRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private DriveObjectStoragePort objectStoragePort;

    @SpyBean
    private DriveTransactionOperations transactionOperations;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from drive_share_access");
        jdbcTemplate.update("delete from drive_share");
        jdbcTemplate.update("delete from drive_upload");
        jdbcTemplate.update("delete from drive_entry");
        jdbcTemplate.update("delete from drive_space");
    }

    @Test
    void concurrentPermanentDeletesShouldReleaseQuotaOnceAndAttemptEveryObjectCleanup() throws Exception {
        UUID userId = uuid(7);
        Instant now = Instant.parse("2026-05-09T00:00:00Z");
        DriveSpace space = DriveSpace.createDefault(uuid(80), userId, now)
                .reserve(20, now)
                .commitReserved(20, now);
        spaceRepository.save(space);

        UUID folderId = uuid(81);
        UUID firstObjectId = uuid(82);
        UUID secondObjectId = uuid(83);
        DriveEntry folder = DriveEntry.folder(folderId, space.spaceId(), null, "folder", now)
                .trash(now, now.plusSeconds(86_400));
        DriveEntry firstFile = DriveEntry.file(
                        uuid(84), space.spaceId(), folderId, "a.txt", firstObjectId, uuid(85), 8, "text/plain", now
                )
                .trash(folderId, now, now.plusSeconds(86_400));
        DriveEntry secondFile = DriveEntry.file(
                        uuid(86), space.spaceId(), folderId, "b.txt", secondObjectId, uuid(87), 12, "text/plain", now
                )
                .trash(folderId, now, now.plusSeconds(86_400));
        entryRepository.save(folder);
        entryRepository.save(firstFile);
        entryRepository.save(secondFile);
        assertThat(spaceRepository.findById(space.spaceId()).orElseThrow().usedBytes())
                .isEqualTo(firstFile.sizeBytes() + secondFile.sizeBytes());
        clearInvocations(spaceRepository);

        CountDownLatch bothAtTransactionBoundary = new CountDownLatch(2);
        CountDownLatch allowTransactions = new CountDownLatch(1);
        doAnswer(invocation -> {
            bothAtTransactionBoundary.countDown();
            assertThat(allowTransactions.await(5, TimeUnit.SECONDS)).isTrue();
            return invocation.callRealMethod();
        }).when(transactionOperations).requiresNew(any(Supplier.class));

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = executor.submit(() -> {
                await(start);
                service.deletePermanently(userId, folderId);
            });
            Future<?> second = executor.submit(() -> {
                await(start);
                service.deletePermanently(userId, folderId);
            });

            start.countDown();
            assertThat(bothAtTransactionBoundary.await(5, TimeUnit.SECONDS)).isTrue();
            allowTransactions.countDown();
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);

            assertThat(List.of(folderId, firstFile.entryId(), secondFile.entryId()))
                    .allSatisfy(entryId -> assertThat(entryRepository.findById(space.spaceId(), entryId).orElseThrow().status())
                            .isEqualTo(DriveEntryStatus.DELETED));
            assertThat(spaceRepository.findById(space.spaceId()).orElseThrow().usedBytes()).isZero();
            assertThat(mockingDetails(spaceRepository).getInvocations())
                    .filteredOn(invocation -> invocation.getMethod().getName().equals("save"))
                    .extracting(invocation -> ((DriveSpace) invocation.getArgument(0)).usedBytes())
                    .allMatch(usedBytes -> usedBytes >= 0L);
            verify(spaceRepository, times(1)).save(any(DriveSpace.class));
            verify(objectStoragePort, atLeastOnce()).deleteObject(firstObjectId, userId.toString());
            verify(objectStoragePort, atLeastOnce()).deleteObject(secondObjectId, userId.toString());
        } finally {
            allowTransactions.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void deletePermanentlyShouldCommitDriveMutationBeforeOssFailureAndRetryCleanup() {
        UUID userId = uuid(7);
        Instant now = Instant.parse("2026-05-09T00:00:00Z");
        DriveSpace space = DriveSpace.createDefault(uuid(80), userId, now)
                .reserve(8, now)
                .commitReserved(8, now);
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
                .hasMessage("网盘存储服务不可用")
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(DriveErrorCode.DRIVE_STORAGE_UNAVAILABLE));

        assertThat(entryRepository.findById(space.spaceId(), fileId).orElseThrow().status()).isEqualTo(DriveEntryStatus.DELETED);
        assertThat(spaceRepository.findById(space.spaceId()).orElseThrow().usedBytes()).isZero();

        service.deletePermanently(userId, fileId);

        assertThat(spaceRepository.findById(space.spaceId()).orElseThrow().usedBytes()).isZero();
        verify(objectStoragePort, times(2)).deleteObject(objectId, userId.toString());
    }

    @Test
    void deletedParentRetryShouldCleanupEarlierDeletedChildWithoutReleasingQuotaAgain() {
        UUID userId = uuid(7);
        Instant now = Instant.parse("2026-05-09T00:00:00Z");
        DriveSpace space = DriveSpace.createDefault(uuid(90), userId, now)
                .reserve(8, now)
                .commitReserved(8, now);
        spaceRepository.save(space);

        UUID folderId = uuid(91);
        UUID fileId = uuid(92);
        UUID objectId = uuid(93);
        DriveEntry folder = DriveEntry.folder(folderId, space.spaceId(), null, "folder", now)
                .trash(now, now.plusSeconds(86_400));
        DriveEntry child = DriveEntry.file(
                        fileId, space.spaceId(), folderId, "child.txt", objectId, uuid(94), 8, "text/plain", now
                )
                .trash(folderId, now, now.plusSeconds(86_400));
        entryRepository.save(folder);
        entryRepository.save(child);
        clearInvocations(spaceRepository);

        doThrow(new RuntimeException("oss down"))
                .doNothing()
                .when(objectStoragePort)
                .deleteObject(objectId, userId.toString());

        assertThatThrownBy(() -> serviceAt(now.plusSeconds(1)).deletePermanently(userId, fileId))
                .isInstanceOf(BusinessException.class)
                .hasMessage("网盘存储服务不可用");

        assertThat(entryRepository.findById(space.spaceId(), folderId).orElseThrow().status())
                .isEqualTo(DriveEntryStatus.TRASHED);
        assertThat(entryRepository.findById(space.spaceId(), fileId).orElseThrow().status())
                .isEqualTo(DriveEntryStatus.DELETED);
        assertThat(spaceRepository.findById(space.spaceId()).orElseThrow().usedBytes()).isZero();

        serviceAt(now.plusSeconds(2)).deletePermanently(userId, folderId);
        serviceAt(now.plusSeconds(3)).deletePermanently(userId, folderId);

        assertThat(entryRepository.findById(space.spaceId(), folderId).orElseThrow().status())
                .isEqualTo(DriveEntryStatus.DELETED);
        assertThat(spaceRepository.findById(space.spaceId()).orElseThrow().usedBytes()).isZero();
        verify(spaceRepository, times(1)).save(any(DriveSpace.class));
        verify(objectStoragePort, times(2)).deleteObject(objectId, userId.toString());
    }

    private DriveTrashApplicationService serviceAt(Instant now) {
        return new DriveTrashApplicationService(
                spaceRepository,
                entryRepository,
                objectStoragePort,
                Clock.fixed(now, ZoneOffset.UTC),
                transactionOperations
        );
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted while awaiting concurrent delete start", e);
        }
    }
}
