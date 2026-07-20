package com.nowcoder.community.drive.application;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.drive.application.port.DriveObjectStoragePort;
import com.nowcoder.community.drive.application.result.DriveEntryResult;
import com.nowcoder.community.drive.domain.model.DriveEntry;
import com.nowcoder.community.drive.domain.model.DriveEntryStatus;
import com.nowcoder.community.drive.domain.model.DriveSpace;
import com.nowcoder.community.drive.domain.repository.DriveEntryRepository;
import com.nowcoder.community.drive.domain.repository.DriveSpaceRepository;
import com.nowcoder.community.drive.exception.DriveErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static com.nowcoder.community.common.exception.CommonErrorCode.INTERNAL_ERROR;
import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;

@Service
public class DriveTrashApplicationService {

    private static final long TRASH_RETENTION_SECONDS = 30L * 24L * 60L * 60L;

    private final DriveSpaceRepository spaceRepository;
    private final DriveEntryRepository entryRepository;
    private final DriveObjectStoragePort objectStoragePort;
    private final Clock clock;
    private final DriveTransactionOperations transactionOperations;

    @Autowired
    public DriveTrashApplicationService(
            DriveSpaceRepository spaceRepository,
            DriveEntryRepository entryRepository,
            DriveObjectStoragePort objectStoragePort,
            Clock clock,
            DriveTransactionOperations transactionOperations
    ) {
        this.spaceRepository = spaceRepository;
        this.entryRepository = entryRepository;
        this.objectStoragePort = objectStoragePort;
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.transactionOperations = Objects.requireNonNull(
                transactionOperations,
                "transactionOperations must not be null"
        );
    }

    DriveTrashApplicationService(
            DriveSpaceRepository spaceRepository,
            DriveEntryRepository entryRepository,
            DriveObjectStoragePort objectStoragePort,
            Clock clock
    ) {
        this(
                spaceRepository,
                entryRepository,
                objectStoragePort,
                clock,
                DirectDriveTransactionOperations.INSTANCE
        );
    }

    @Transactional
    public DriveEntryResult trash(UUID actorUserId, UUID entryId) {
        DriveSpace space = loadOrCreateSpace(actorUserId);
        lockSpace(space.spaceId());
        DriveEntry entry = loadEntry(space.spaceId(), entryId);
        if (entry.status() != DriveEntryStatus.ACTIVE) {
            throw new BusinessException(DriveErrorCode.DRIVE_ENTRY_NOT_FOUND, "网盘条目不存在");
        }
        Instant now = clock.instant();
        Instant deleteAfter = now.plusSeconds(TRASH_RETENTION_SECONDS);
        List<DriveEntry> entries = entryWithDescendants(space.spaceId(), entry);
        entries.forEach(item -> {
            if (item.status() == DriveEntryStatus.ACTIVE) {
                entryRepository.save(item.trash(entry.entryId(), now, deleteAfter));
            }
        });
        return DriveEntryApplicationService.toEntryResult(loadEntry(space.spaceId(), entryId));
    }

    @Transactional
    public DriveEntryResult restore(UUID actorUserId, UUID entryId, UUID targetParentId) {
        DriveSpace space = loadOrCreateSpace(actorUserId);
        lockSpace(space.spaceId());
        DriveEntry entry = loadEntry(space.spaceId(), entryId);
        if (entry.status() != DriveEntryStatus.TRASHED) {
            throw new BusinessException(DriveErrorCode.DRIVE_ENTRY_TRASHED, "回收站条目不可执行该操作");
        }
        if (!entry.entryId().equals(entry.trashRootId())) {
            throw new BusinessException(DriveErrorCode.DRIVE_ENTRY_TRASHED, "回收站条目不可执行该操作");
        }
        validateParent(targetParentId, space.spaceId());
        rejectDuplicate(space.spaceId(), targetParentId, entry.name(), entry.entryId());
        Instant now = clock.instant();
        DriveEntry restored = entry.restore(targetParentId, now);
        entryRepository.save(restored);
        entryRepository.listDescendantIds(space.spaceId(), entry.entryId()).stream()
                .map(descendantId -> loadEntry(space.spaceId(), descendantId))
                .filter(descendant -> descendant.status() == DriveEntryStatus.TRASHED)
                .filter(descendant -> entry.entryId().equals(descendant.trashRootId()))
                .map(descendant -> descendant.restore(descendant.parentId(), now))
                .forEach(entryRepository::save);
        return DriveEntryApplicationService.toEntryResult(restored);
    }

    public void deletePermanently(UUID actorUserId, UUID entryId) {
        UUID userId = requireUser(actorUserId);
        PermanentDeletionWork work = transactionOperations.requiresNew(
                () -> preparePermanentDeletion(userId, entryId)
        );
        deleteObjects(work.cleanupTargets(), userId);
    }

    private PermanentDeletionWork preparePermanentDeletion(UUID actorUserId, UUID entryId) {
        DriveSpace space = loadOrCreateSpace(actorUserId);
        DriveSpace latest = lockSpace(space.spaceId());
        DriveEntry entry = loadEntry(space.spaceId(), entryId);
        if (entry.status() == DriveEntryStatus.ACTIVE) {
            throw new BusinessException(DriveErrorCode.DRIVE_ENTRY_TRASHED, "回收站条目不可执行该操作");
        }
        List<DriveEntry> entries = entryWithDescendants(space.spaceId(), entry);
        if (entry.status() == DriveEntryStatus.TRASHED) {
            Instant now = clock.instant();
            List<DriveEntry> winners = new ArrayList<>();
            for (DriveEntry item : entries) {
                if (item.status() != DriveEntryStatus.TRASHED) {
                    continue;
                }
                DriveEntry deleted = item.delete(now);
                if (entryRepository.markDeletedIfTrashed(deleted)) {
                    winners.add(deleted);
                }
            }
            long releasedBytes = winners.stream()
                    .filter(DriveEntry::file)
                    .mapToLong(DriveEntry::sizeBytes)
                    .sum();
            if (releasedBytes > 0) {
                spaceRepository.save(latest.release(releasedBytes, now));
            }
            return new PermanentDeletionWork(winners);
        }
        Instant retryWatermark = entry.updatedAt();
        List<DriveEntry> retryTargets = entries.stream()
                .filter(DriveEntry::file)
                .filter(item -> item.status() == DriveEntryStatus.DELETED)
                .filter(item -> retryWatermark != null && !item.updatedAt().isBefore(retryWatermark))
                .toList();
        return new PermanentDeletionWork(retryTargets);
    }

    public List<DriveEntryResult> listTrash(UUID actorUserId) {
        DriveSpace space = loadOrCreateSpace(actorUserId);
        return entryRepository.listTrash(space.spaceId()).stream()
                .map(DriveEntryApplicationService::toEntryResult)
                .toList();
    }

    private List<DriveEntry> entryWithDescendants(UUID spaceId, DriveEntry entry) {
        List<DriveEntry> entries = new ArrayList<>();
        entries.add(entry);
        entryRepository.listDescendantIds(spaceId, entry.entryId()).stream()
                .map(descendantId -> loadEntry(spaceId, descendantId))
                .forEach(entries::add);
        return entries;
    }

    private DriveSpace loadOrCreateSpace(UUID actorUserId) {
        UUID userId = requireUser(actorUserId);
        Instant now = clock.instant();
        return spaceRepository.findByUserId(userId)
                .orElseGet(() -> createDefaultSpace(userId, now));
    }

    private DriveSpace createDefaultSpace(UUID userId, Instant now) {
        DriveSpace space = DriveSpace.createDefault(UUID.randomUUID(), userId, now);
        DriveSpaceRepository.CreateResult result = spaceRepository.create(space);
        if (result != null
                && (result.status() == DriveSpaceRepository.CreateStatus.CREATED
                || result.status() == DriveSpaceRepository.CreateStatus.ALREADY_EXISTS)
                && result.space() != null) {
            return result.space();
        }
        throw new BusinessException(INTERNAL_ERROR, "网盘空间创建失败");
    }

    private DriveSpace lockSpace(UUID spaceId) {
        DriveSpace locked = spaceRepository.lockById(spaceId);
        if (locked == null) {
            throw new BusinessException(DriveErrorCode.DRIVE_SPACE_NOT_FOUND, "网盘空间不存在");
        }
        return locked;
    }

    private DriveEntry loadEntry(UUID spaceId, UUID entryId) {
        if (entryId == null) {
            throw new BusinessException(DriveErrorCode.DRIVE_ENTRY_NOT_FOUND, "网盘条目不存在");
        }
        return entryRepository.findById(spaceId, entryId)
                .orElseThrow(() -> new BusinessException(DriveErrorCode.DRIVE_ENTRY_NOT_FOUND, "网盘条目不存在"));
    }

    private void validateParent(UUID parentId, UUID spaceId) {
        if (parentId == null) {
            return;
        }
        DriveEntry parent = entryRepository.findById(spaceId, parentId)
                .orElseThrow(() -> new BusinessException(DriveErrorCode.DRIVE_PARENT_NOT_FOUND, "目标文件夹不存在"));
        if (!parent.folder() || parent.status() != DriveEntryStatus.ACTIVE) {
            throw new BusinessException(DriveErrorCode.DRIVE_PARENT_NOT_FOUND, "目标文件夹不存在");
        }
    }

    private void rejectDuplicate(UUID spaceId, UUID parentId, String name, UUID currentEntryId) {
        entryRepository.findActiveChildByName(spaceId, parentId, name)
                .filter(entry -> !entry.entryId().equals(currentEntryId))
                .ifPresent(entry -> {
                    throw new BusinessException(DriveErrorCode.DRIVE_DUPLICATE_NAME, "同名文件或文件夹已存在");
                });
    }

    private void deleteObjects(List<DriveEntry> targets, UUID actorUserId) {
        try {
            targets.stream()
                    .filter(DriveEntry::file)
                    .map(DriveEntry::objectId)
                    .distinct()
                    .forEach(objectId -> objectStoragePort.deleteObject(objectId, actorUserId.toString()));
        } catch (BusinessException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new BusinessException(DriveErrorCode.DRIVE_STORAGE_UNAVAILABLE, "网盘存储服务不可用", e);
        }
    }

    private static UUID requireUser(UUID actorUserId) {
        if (actorUserId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "actorUserId 非法");
        }
        return actorUserId;
    }

    private record PermanentDeletionWork(List<DriveEntry> cleanupTargets) {
        private PermanentDeletionWork {
            cleanupTargets = cleanupTargets == null ? List.of() : List.copyOf(cleanupTargets);
        }
    }
}
