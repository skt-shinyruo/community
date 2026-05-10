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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;

@Service
public class DriveTrashApplicationService {

    private static final long TRASH_RETENTION_SECONDS = 30L * 24L * 60L * 60L;

    private final DriveSpaceRepository spaceRepository;
    private final DriveEntryRepository entryRepository;
    private final DriveObjectStoragePort objectStoragePort;
    private final Clock clock;

    public DriveTrashApplicationService(
            DriveSpaceRepository spaceRepository,
            DriveEntryRepository entryRepository,
            DriveObjectStoragePort objectStoragePort,
            Clock clock
    ) {
        this.spaceRepository = spaceRepository;
        this.entryRepository = entryRepository;
        this.objectStoragePort = objectStoragePort;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    @Transactional
    public DriveEntryResult trash(UUID actorUserId, UUID entryId) {
        DriveSpace space = loadSpace(actorUserId);
        DriveEntry entry = loadEntry(space.spaceId(), entryId);
        if (entry.status() != DriveEntryStatus.ACTIVE) {
            throw new BusinessException(DriveErrorCode.DRIVE_ENTRY_NOT_FOUND, "网盘条目不存在");
        }
        Instant now = clock.instant();
        Instant deleteAfter = now.plusSeconds(TRASH_RETENTION_SECONDS);
        List<DriveEntry> entries = entryWithDescendants(space.spaceId(), entry);
        entries.forEach(item -> {
            if (item.status() == DriveEntryStatus.ACTIVE) {
                entryRepository.save(item.trash(now, deleteAfter));
            }
        });
        return DriveEntryApplicationService.toEntryResult(loadEntry(space.spaceId(), entryId));
    }

    @Transactional
    public DriveEntryResult restore(UUID actorUserId, UUID entryId, UUID targetParentId) {
        DriveSpace space = loadSpace(actorUserId);
        DriveEntry entry = loadEntry(space.spaceId(), entryId);
        if (entry.status() != DriveEntryStatus.TRASHED) {
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
                .map(descendant -> descendant.restore(descendant.parentId(), now))
                .forEach(entryRepository::save);
        return DriveEntryApplicationService.toEntryResult(restored);
    }

    @Transactional
    public void deletePermanently(UUID actorUserId, UUID entryId) {
        DriveSpace space = loadSpace(actorUserId);
        DriveEntry entry = loadEntry(space.spaceId(), entryId);
        if (entry.status() == DriveEntryStatus.DELETED) {
            return;
        }
        if (entry.status() != DriveEntryStatus.TRASHED) {
            throw new BusinessException(DriveErrorCode.DRIVE_ENTRY_TRASHED, "回收站条目不可执行该操作");
        }
        List<DriveEntry> entries = entryWithDescendants(space.spaceId(), entry);
        long releasedBytes = entries.stream()
                .filter(DriveEntry::file)
                .mapToLong(DriveEntry::sizeBytes)
                .sum();
        Instant now = clock.instant();
        entries.forEach(item -> entryRepository.save(item.delete(now)));
        if (releasedBytes > 0) {
            DriveSpace latest = spaceRepository.findById(space.spaceId()).orElse(space);
            spaceRepository.save(latest.release(releasedBytes, now));
        }
        entries.stream()
                .filter(DriveEntry::file)
                .map(DriveEntry::objectId)
                .distinct()
                .forEach(objectId -> objectStoragePort.deleteObject(objectId, actorUserId.toString()));
    }

    @Transactional(readOnly = true)
    public List<DriveEntryResult> listTrash(UUID actorUserId) {
        DriveSpace space = loadSpace(actorUserId);
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

    private DriveSpace loadSpace(UUID actorUserId) {
        UUID userId = requireUser(actorUserId);
        return spaceRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(DriveErrorCode.DRIVE_SPACE_NOT_FOUND, "网盘空间不存在"));
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

    private static UUID requireUser(UUID actorUserId) {
        if (actorUserId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "actorUserId 非法");
        }
        return actorUserId;
    }
}
