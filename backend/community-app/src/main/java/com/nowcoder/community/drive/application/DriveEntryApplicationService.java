package com.nowcoder.community.drive.application;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.drive.application.command.CreateDriveFolderCommand;
import com.nowcoder.community.drive.application.command.MoveDriveEntryCommand;
import com.nowcoder.community.drive.application.command.RenameDriveEntryCommand;
import com.nowcoder.community.drive.application.port.DriveObjectStoragePort;
import com.nowcoder.community.drive.application.result.DriveDownloadUrlResult;
import com.nowcoder.community.drive.application.result.DriveEntryResult;
import com.nowcoder.community.drive.domain.model.DriveEntry;
import com.nowcoder.community.drive.domain.model.DriveEntryStatus;
import com.nowcoder.community.drive.domain.model.DriveSpace;
import com.nowcoder.community.drive.domain.repository.DriveEntryRepository;
import com.nowcoder.community.drive.domain.repository.DriveSpaceRepository;
import com.nowcoder.community.drive.domain.service.DriveEntryDomainService;
import com.nowcoder.community.drive.exception.DriveErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static com.nowcoder.community.common.exception.CommonErrorCode.INTERNAL_ERROR;
import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;

@Service
public class DriveEntryApplicationService {

    private static final int SEARCH_LIMIT = 50;
    private static final long DOWNLOAD_TTL_SECONDS = 600L;

    private final DriveSpaceRepository spaceRepository;
    private final DriveEntryRepository entryRepository;
    private final DriveObjectStoragePort objectStoragePort;
    private final Clock clock;
    private final DriveEntryDomainService entryDomainService = new DriveEntryDomainService();

    public DriveEntryApplicationService(
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
    public DriveEntryResult createFolder(CreateDriveFolderCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        DriveSpace space = loadOrCreateSpace(command.actorUserId());
        lockSpace(space.spaceId());
        validateParent(command.parentId(), space.spaceId());
        String name = normalizeName(command.name());
        rejectDuplicate(space.spaceId(), command.parentId(), name, null);
        DriveEntry folder = DriveEntry.folder(UUID.randomUUID(), space.spaceId(), command.parentId(), name, clock.instant());
        return toEntryResult(createEntry(folder));
    }

    @Transactional
    public List<DriveEntryResult> listEntries(UUID actorUserId, UUID parentId) {
        DriveSpace space = loadSpace(actorUserId);
        validateParent(parentId, space.spaceId());
        return entryRepository.listActiveChildren(space.spaceId(), parentId).stream()
                .map(DriveEntryApplicationService::toEntryResult)
                .toList();
    }

    @Transactional
    public List<DriveEntryResult> search(UUID actorUserId, String keyword) {
        DriveSpace space = loadSpace(actorUserId);
        String normalizedKeyword = Objects.toString(keyword, "").trim();
        if (normalizedKeyword.isBlank()) {
            return List.of();
        }
        return entryRepository.searchActive(space.spaceId(), normalizedKeyword, SEARCH_LIMIT).stream()
                .map(DriveEntryApplicationService::toEntryResult)
                .toList();
    }

    @Transactional
    public DriveEntryResult rename(RenameDriveEntryCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        if (command.entryId() == null) {
            throw new BusinessException(INVALID_ARGUMENT, "重命名参数非法");
        }
        DriveSpace space = loadSpace(command.actorUserId());
        lockSpace(space.spaceId());
        DriveEntry entry = loadActiveEntry(space.spaceId(), command.entryId());
        String newName = normalizeName(command.newName());
        rejectDuplicate(space.spaceId(), entry.parentId(), newName, entry.entryId());
        DriveEntry renamed = entry.rename(newName, clock.instant());
        entryRepository.save(renamed);
        return toEntryResult(renamed);
    }

    @Transactional
    public DriveEntryResult move(MoveDriveEntryCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        if (command.entryId() == null) {
            throw new BusinessException(INVALID_ARGUMENT, "移动参数非法");
        }
        DriveSpace space = loadSpace(command.actorUserId());
        lockSpace(space.spaceId());
        DriveEntry entry = loadActiveEntry(space.spaceId(), command.entryId());
        validateParent(command.targetParentId(), space.spaceId());
        if (entry.folder()) {
            assertCanMove(entry.entryId(), command.targetParentId(), entryRepository.listDescendantIds(space.spaceId(), entry.entryId()));
        }
        rejectDuplicate(space.spaceId(), command.targetParentId(), entry.name(), entry.entryId());
        DriveEntry moved = entry.moveTo(command.targetParentId(), clock.instant());
        entryRepository.save(moved);
        return toEntryResult(moved);
    }

    @Transactional
    public DriveDownloadUrlResult createDownloadUrl(UUID actorUserId, UUID entryId) {
        DriveSpace space = loadSpace(actorUserId);
        DriveEntry entry = loadActiveEntry(space.spaceId(), entryId);
        if (!entry.file()) {
            throw new BusinessException(DriveErrorCode.DRIVE_ENTRY_NOT_FOUND, "网盘条目不存在");
        }
        DriveObjectStoragePort.SignedDownloadUrl signedUrl = objectStoragePort.createDownloadUrl(entry.objectId(), DOWNLOAD_TTL_SECONDS);
        if (signedUrl == null || signedUrl.url() == null || signedUrl.url().isBlank() || signedUrl.expiresAt() == null) {
            throw new BusinessException(DriveErrorCode.DRIVE_STORAGE_UNAVAILABLE, "网盘存储服务不可用");
        }
        return new DriveDownloadUrlResult(entry.entryId(), signedUrl.url(), signedUrl.expiresAt());
    }

    private DriveSpace loadOrCreateSpace(UUID actorUserId) {
        UUID userId = requireUser(actorUserId);
        Instant now = clock.instant();
        return spaceRepository.findByUserId(userId)
                .orElseGet(() -> createDefaultSpace(userId, now));
    }

    private DriveSpace loadSpace(UUID actorUserId) {
        return loadOrCreateSpace(actorUserId);
    }

    private void lockSpace(UUID spaceId) {
        if (spaceRepository.lockById(spaceId) == null) {
            throw new BusinessException(DriveErrorCode.DRIVE_SPACE_NOT_FOUND, "网盘空间不存在");
        }
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

    private DriveEntry createEntry(DriveEntry entry) {
        DriveEntryRepository.CreateResult result = entryRepository.create(entry);
        if (result == null || result.status() == DriveEntryRepository.CreateStatus.CONFLICT) {
            throw new BusinessException(INTERNAL_ERROR, "网盘条目创建失败");
        }
        if (result.status() == DriveEntryRepository.CreateStatus.ACTIVE_NAME_CONFLICT) {
            throw new BusinessException(DriveErrorCode.DRIVE_DUPLICATE_NAME, "同名文件或文件夹已存在");
        }
        if (result.entry() == null) {
            throw new BusinessException(INTERNAL_ERROR, "网盘条目创建失败");
        }
        return result.entry();
    }

    private DriveEntry loadActiveEntry(UUID spaceId, UUID entryId) {
        if (entryId == null) {
            throw new BusinessException(DriveErrorCode.DRIVE_ENTRY_NOT_FOUND, "网盘条目不存在");
        }
        DriveEntry entry = entryRepository.findById(spaceId, entryId)
                .orElseThrow(() -> new BusinessException(DriveErrorCode.DRIVE_ENTRY_NOT_FOUND, "网盘条目不存在"));
        if (entry.status() != DriveEntryStatus.ACTIVE) {
            throw new BusinessException(DriveErrorCode.DRIVE_ENTRY_NOT_FOUND, "网盘条目不存在");
        }
        return entry;
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

    private void assertCanMove(UUID entryId, UUID targetParentId, List<UUID> descendantIds) {
        try {
            entryDomainService.assertCanMove(entryId, targetParentId, descendantIds);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(DriveErrorCode.DRIVE_INVALID_MOVE, "不能移动到自身或子目录");
        }
    }

    private String normalizeName(String name) {
        try {
            return entryDomainService.normalizeName(name);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(INVALID_ARGUMENT, e.getMessage());
        }
    }

    static DriveEntryResult toEntryResult(DriveEntry entry) {
        return new DriveEntryResult(
                entry.entryId(),
                entry.parentId(),
                entry.type().name(),
                entry.name(),
                entry.sizeBytes(),
                entry.mimeType(),
                entry.status().name(),
                entry.updatedAt()
        );
    }

    private static UUID requireUser(UUID actorUserId) {
        if (actorUserId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "actorUserId 非法");
        }
        return actorUserId;
    }
}
