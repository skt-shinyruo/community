package com.nowcoder.community.drive.application;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.drive.application.command.CreateDriveShareCommand;
import com.nowcoder.community.drive.application.command.VerifyDriveShareCommand;
import com.nowcoder.community.drive.application.port.DriveObjectStoragePort;
import com.nowcoder.community.drive.application.port.DrivePasswordHasher;
import com.nowcoder.community.drive.application.port.DriveShareTicketCodec;
import com.nowcoder.community.drive.application.result.DriveDownloadUrlResult;
import com.nowcoder.community.drive.application.result.DriveEntryResult;
import com.nowcoder.community.drive.application.result.DrivePublicShareGateResult;
import com.nowcoder.community.drive.application.result.DriveShareResult;
import com.nowcoder.community.drive.domain.model.DriveEntry;
import com.nowcoder.community.drive.domain.model.DriveEntryStatus;
import com.nowcoder.community.drive.domain.model.DriveShare;
import com.nowcoder.community.drive.domain.model.DriveSpace;
import com.nowcoder.community.drive.domain.repository.DriveEntryRepository;
import com.nowcoder.community.drive.domain.repository.DriveShareAccessRepository;
import com.nowcoder.community.drive.domain.repository.DriveShareRepository;
import com.nowcoder.community.drive.domain.repository.DriveSpaceRepository;
import com.nowcoder.community.drive.exception.DriveErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static com.nowcoder.community.common.exception.CommonErrorCode.FORBIDDEN;
import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;

@Service
public class DriveShareApplicationService {

    private static final long SHARE_DOWNLOAD_TTL_SECONDS = 600L;
    private static final long TICKET_TTL_SECONDS = 600L;
    private static final int TOKEN_BYTES = 18;

    private final DriveSpaceRepository spaceRepository;
    private final DriveEntryRepository entryRepository;
    private final DriveShareRepository shareRepository;
    private final DriveShareAccessRepository shareAccessRepository;
    private final DriveObjectStoragePort objectStoragePort;
    private final DrivePasswordHasher passwordHasher;
    private final DriveShareTicketCodec ticketCodec;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public DriveShareApplicationService(
            DriveSpaceRepository spaceRepository,
            DriveEntryRepository entryRepository,
            DriveShareRepository shareRepository,
            DriveShareAccessRepository shareAccessRepository,
            DriveObjectStoragePort objectStoragePort,
            DrivePasswordHasher passwordHasher,
            DriveShareTicketCodec ticketCodec,
            Clock clock
    ) {
        this.spaceRepository = spaceRepository;
        this.entryRepository = entryRepository;
        this.shareRepository = shareRepository;
        this.shareAccessRepository = shareAccessRepository;
        this.objectStoragePort = objectStoragePort;
        this.passwordHasher = passwordHasher;
        this.ticketCodec = ticketCodec;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    @Transactional
    public DriveShareResult createShare(CreateDriveShareCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        if (command.entryId() == null) {
            throw new BusinessException(INVALID_ARGUMENT, "分享参数非法");
        }
        UUID actorUserId = requireUser(command.actorUserId());
        String password = requirePassword(command.password());
        Instant now = clock.instant();
        if (command.expiresAt() == null || !command.expiresAt().isAfter(now)) {
            throw new BusinessException(DriveErrorCode.DRIVE_SHARE_INVALID, "分享链接不可用");
        }
        DriveSpace space = loadSpace(actorUserId);
        DriveEntry entry = loadActiveFileOrFolder(space.spaceId(), command.entryId());
        DriveShare share = DriveShare.active(
                UUID.randomUUID(),
                entry.entryId(),
                nextToken(),
                passwordHasher.hash(password),
                command.expiresAt(),
                actorUserId,
                now
        );
        shareRepository.save(share);
        return toShareResult(share, entry, null, null);
    }

    @Transactional
    public void revokeShare(UUID actorUserId, UUID shareId) {
        UUID userId = requireUser(actorUserId);
        if (shareId == null) {
            throw new BusinessException(DriveErrorCode.DRIVE_SHARE_INVALID, "分享链接不可用");
        }
        DriveShare share = shareRepository.findById(shareId)
                .orElseThrow(() -> new BusinessException(DriveErrorCode.DRIVE_SHARE_INVALID, "分享链接不可用"));
        if (!share.createdBy().equals(userId)) {
            throw new BusinessException(FORBIDDEN, "只能撤销自己的分享");
        }
        shareRepository.save(share.revoke(clock.instant()));
    }

    @Transactional(readOnly = true)
    public DrivePublicShareGateResult loadPublicShareGate(String shareToken) {
        DriveShare share = loadActiveShare(shareToken);
        return new DrivePublicShareGateResult(share.shareToken(), true);
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public DriveShareResult verifyShare(VerifyDriveShareCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        DriveShare share = shareRepository.findByToken(command.shareToken())
                .orElseThrow(() -> new BusinessException(DriveErrorCode.DRIVE_SHARE_INVALID, "分享链接不可用"));
        Instant now = clock.instant();
        if (!share.activeAt(now)) {
            recordAccess(share, command.visitorFingerprint(), false, now);
            throw new BusinessException(DriveErrorCode.DRIVE_SHARE_INVALID, "分享链接不可用");
        }
        if (!passwordHasher.matches(command.password(), share.passwordHash())) {
            recordAccess(share, command.visitorFingerprint(), false, now);
            throw new BusinessException(DriveErrorCode.DRIVE_SHARE_PASSWORD_INVALID, "提取码错误");
        }
        DriveEntry entry;
        try {
            entry = loadActiveEntryForShare(share);
        } catch (BusinessException e) {
            recordAccess(share, command.visitorFingerprint(), false, now);
            throw e;
        }
        recordAccess(share, command.visitorFingerprint(), true, now);
        Instant ticketExpiresAt = now.plusSeconds(TICKET_TTL_SECONDS);
        String ticket = ticketCodec.issue(share.shareToken(), ticketExpiresAt);
        return toShareResult(share, entry, ticket, ticketExpiresAt);
    }

    @Transactional(readOnly = true)
    public List<DriveEntryResult> listShareEntries(String shareToken, String ticket, UUID parentId) {
        DriveShare share = loadActiveShare(shareToken);
        if (!ticketCodec.valid(share.shareToken(), ticket, clock.instant())) {
            throw new BusinessException(DriveErrorCode.DRIVE_SHARE_INVALID, "分享链接不可用");
        }
        DriveSpace space = loadSpaceForShare(share);
        DriveEntry shareEntry = loadActiveEntry(space.spaceId(), share.entryId());
        DriveEntry parent = loadShareListParent(space.spaceId(), shareEntry, parentId);
        if (parent == null) {
            return List.of();
        }
        return entryRepository.listActiveChildren(space.spaceId(), parent.entryId()).stream()
                .map(DriveShareApplicationService::toEntryResult)
                .toList();
    }

    @Transactional(readOnly = true)
    public DriveDownloadUrlResult createShareDownloadUrl(String shareToken, String ticket, UUID entryId) {
        DriveShare share = loadActiveShare(shareToken);
        if (!ticketCodec.valid(share.shareToken(), ticket, clock.instant())) {
            throw new BusinessException(DriveErrorCode.DRIVE_SHARE_INVALID, "分享链接不可用");
        }
        DriveSpace space = loadSpaceForShare(share);
        DriveEntry shareEntry = loadActiveEntry(space.spaceId(), share.entryId());
        DriveEntry entry = loadShareDownloadTarget(space.spaceId(), shareEntry, entryId);
        if (!entry.file()) {
            throw new BusinessException(DriveErrorCode.DRIVE_SHARE_INVALID, "分享链接不可用");
        }
        DriveObjectStoragePort.SignedDownloadUrl signedUrl = objectStoragePort.createDownloadUrl(entry.objectId(), SHARE_DOWNLOAD_TTL_SECONDS);
        if (signedUrl == null || signedUrl.url() == null || signedUrl.url().isBlank() || signedUrl.expiresAt() == null) {
            throw new BusinessException(DriveErrorCode.DRIVE_STORAGE_UNAVAILABLE, "网盘存储服务不可用");
        }
        return new DriveDownloadUrlResult(entry.entryId(), signedUrl.url(), signedUrl.expiresAt());
    }

    private DriveShare loadActiveShare(String shareToken) {
        DriveShare share = shareRepository.findByToken(shareToken)
                .orElseThrow(() -> new BusinessException(DriveErrorCode.DRIVE_SHARE_INVALID, "分享链接不可用"));
        if (!share.activeAt(clock.instant())) {
            throw new BusinessException(DriveErrorCode.DRIVE_SHARE_INVALID, "分享链接不可用");
        }
        return share;
    }

    private DriveEntry loadActiveEntryForShare(DriveShare share) {
        DriveSpace space = loadSpaceForShare(share);
        return loadActiveEntry(space.spaceId(), share.entryId());
    }

    private DriveEntry loadActiveFileOrFolder(UUID spaceId, UUID entryId) {
        return entryRepository.findById(spaceId, entryId)
                .filter(entry -> entry.status() == DriveEntryStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(DriveErrorCode.DRIVE_ENTRY_NOT_FOUND, "网盘条目不存在"));
    }

    private DriveSpace loadSpace(UUID actorUserId) {
        return spaceRepository.findByUserId(actorUserId)
                .orElseThrow(() -> new BusinessException(DriveErrorCode.DRIVE_SPACE_NOT_FOUND, "网盘空间不存在"));
    }

    private DriveSpace loadSpaceForShare(DriveShare share) {
        return spaceRepository.findByUserId(share.createdBy())
                .orElseThrow(() -> new BusinessException(DriveErrorCode.DRIVE_SHARE_INVALID, "分享链接不可用"));
    }

    private DriveEntry loadActiveEntry(UUID spaceId, UUID entryId) {
        return entryRepository.findById(spaceId, entryId)
                .filter(entry -> entry.status() == DriveEntryStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(DriveErrorCode.DRIVE_SHARE_INVALID, "分享链接不可用"));
    }

    private DriveEntry loadShareDownloadTarget(UUID spaceId, DriveEntry shareEntry, UUID entryId) {
        if (entryId == null) {
            throw new BusinessException(DriveErrorCode.DRIVE_SHARE_INVALID, "分享链接不可用");
        }
        if (shareEntry.file()) {
            if (!shareEntry.entryId().equals(entryId)) {
                throw new BusinessException(DriveErrorCode.DRIVE_SHARE_INVALID, "分享链接不可用");
            }
            return shareEntry;
        }
        DriveEntry target = loadActiveEntry(spaceId, entryId);
        if (!target.file()) {
            throw new BusinessException(DriveErrorCode.DRIVE_SHARE_INVALID, "分享链接不可用");
        }
        if (!entryRepository.listDescendantIds(spaceId, shareEntry.entryId()).contains(entryId)) {
            throw new BusinessException(DriveErrorCode.DRIVE_SHARE_INVALID, "分享链接不可用");
        }
        return target;
    }

    private DriveEntry loadShareListParent(UUID spaceId, DriveEntry shareEntry, UUID parentId) {
        if (shareEntry.file()) {
            if (parentId == null || shareEntry.entryId().equals(parentId)) {
                return null;
            }
            throw new BusinessException(DriveErrorCode.DRIVE_SHARE_INVALID, "分享链接不可用");
        }
        if (parentId == null || shareEntry.entryId().equals(parentId)) {
            return shareEntry;
        }
        DriveEntry parent = loadActiveEntry(spaceId, parentId);
        if (!parent.folder()) {
            throw new BusinessException(DriveErrorCode.DRIVE_SHARE_INVALID, "分享链接不可用");
        }
        if (!entryRepository.listDescendantIds(spaceId, shareEntry.entryId()).contains(parentId)) {
            throw new BusinessException(DriveErrorCode.DRIVE_SHARE_INVALID, "分享链接不可用");
        }
        return parent;
    }

    private void recordAccess(DriveShare share, String visitorFingerprint, boolean success, Instant now) {
        shareAccessRepository.record(UUID.randomUUID(), share.shareId(), visitorFingerprint, success, now);
    }

    private String nextToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static DriveShareResult toShareResult(DriveShare share, DriveEntry entry, String ticket, Instant ticketExpiresAt) {
        return new DriveShareResult(
                share.shareId(),
                share.entryId(),
                share.shareToken(),
                entry.name(),
                entry.type().name(),
                share.expiresAt(),
                share.status().name(),
                ticket,
                ticketExpiresAt
        );
    }

    private static DriveEntryResult toEntryResult(DriveEntry entry) {
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

    private static String requirePassword(String password) {
        if (password == null || password.isBlank()) {
            throw new BusinessException(DriveErrorCode.DRIVE_SHARE_PASSWORD_INVALID, "提取码错误");
        }
        return password.trim();
    }

    private static UUID requireUser(UUID actorUserId) {
        if (actorUserId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "actorUserId 非法");
        }
        return actorUserId;
    }
}
