package com.nowcoder.community.drive.application;

import com.nowcoder.community.drive.application.command.CompleteDriveUploadCommand;
import com.nowcoder.community.drive.application.command.DriveUploadContent;
import com.nowcoder.community.drive.application.command.PrepareDriveUploadCommand;
import com.nowcoder.community.drive.application.port.DriveObjectStoragePort;
import com.nowcoder.community.drive.application.port.DrivePasswordHasher;
import com.nowcoder.community.drive.application.port.DriveShareTicketCodec;
import com.nowcoder.community.drive.application.result.DriveEntryResult;
import com.nowcoder.community.drive.application.result.DriveUploadSessionResult;
import com.nowcoder.community.drive.domain.model.DriveEntry;
import com.nowcoder.community.drive.domain.model.DriveEntryStatus;
import com.nowcoder.community.drive.domain.model.DriveEntryType;
import com.nowcoder.community.drive.domain.model.DriveShare;
import com.nowcoder.community.drive.domain.model.DriveSpace;
import com.nowcoder.community.drive.domain.model.DriveUpload;
import com.nowcoder.community.drive.domain.model.DriveUploadStatus;
import com.nowcoder.community.drive.domain.repository.DriveEntryRepository;
import com.nowcoder.community.drive.domain.repository.DriveShareAccessRepository;
import com.nowcoder.community.drive.domain.repository.DriveShareRepository;
import com.nowcoder.community.drive.domain.repository.DriveSpaceRepository;
import com.nowcoder.community.drive.domain.repository.DriveUploadRepository;

import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;

final class TestDriveFixture {

    static final Instant NOW = Instant.parse("2026-05-09T00:00:00Z");
    static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private final InMemoryDriveSpaceRepository spaces = new InMemoryDriveSpaceRepository();
    private final InMemoryDriveEntryRepository entries = new InMemoryDriveEntryRepository();
    private final InMemoryDriveUploadRepository uploads = new InMemoryDriveUploadRepository();
    private final InMemoryDriveShareRepository shares = new InMemoryDriveShareRepository();
    private final InMemoryDriveShareAccessRepository shareAccesses = new InMemoryDriveShareAccessRepository();
    private final FakeStoragePort storage = new FakeStoragePort();
    private final FakePasswordHasher passwordHasher = new FakePasswordHasher();
    private final FakeTicketCodec ticketCodec = new FakeTicketCodec();

    private TestDriveFixture() {
    }

    static TestDriveFixture create() {
        return new TestDriveFixture();
    }

    DriveSpaceApplicationService spaceService() {
        return new DriveSpaceApplicationService(spaces, CLOCK);
    }

    DriveEntryApplicationService entryService() {
        return new DriveEntryApplicationService(spaces, entries, storage, CLOCK);
    }

    DriveUploadApplicationService uploadService() {
        return new DriveUploadApplicationService(spaces, entries, uploads, storage, CLOCK);
    }

    DriveTrashApplicationService trashService() {
        return new DriveTrashApplicationService(spaces, entries, storage, CLOCK);
    }

    DriveShareApplicationService shareService() {
        return new DriveShareApplicationService(spaces, entries, shares, shareAccesses, storage, passwordHasher, ticketCodec, CLOCK);
    }

    UUID createFile(UUID userId, String name, long sizeBytes) {
        DriveUploadSessionResult session = uploadService().prepareUpload(new PrepareDriveUploadCommand(userId, null, name, "text/plain", sizeBytes, ""));
        DriveEntryResult entry = uploadService().completeUpload(new CompleteDriveUploadCommand(
                userId,
                UUID.fromString(session.uploadId()),
                content("content", sizeBytes)
        ));
        return entry.entryId();
    }

    UUID createFile(UUID userId, UUID parentId, String name, long sizeBytes) {
        DriveUploadSessionResult session = uploadService().prepareUpload(new PrepareDriveUploadCommand(userId, parentId, name, "text/plain", sizeBytes, ""));
        DriveEntryResult entry = uploadService().completeUpload(new CompleteDriveUploadCommand(
                userId,
                UUID.fromString(session.uploadId()),
                content("content", sizeBytes)
        ));
        return entry.entryId();
    }

    DriveUploadContent content(String body, long contentLength) {
        return new DriveUploadContent(() -> new ByteArrayInputStream(body.getBytes()), "text/plain", contentLength, "");
    }

    DriveSpace space(UUID userId) {
        return spaces.findByUserId(userId).orElseThrow();
    }

    DriveEntry entry(UUID entryId) {
        return entries.rows.get(entryId);
    }

    DriveShare share(UUID shareId) {
        return shares.findById(shareId).orElseThrow();
    }

    FakeStoragePort storage() {
        return storage;
    }

    InMemoryDriveShareAccessRepository shareAccesses() {
        return shareAccesses;
    }

    FakeTicketCodec ticketCodec() {
        return ticketCodec;
    }

    private static final class InMemoryDriveSpaceRepository implements DriveSpaceRepository {
        private final Map<UUID, DriveSpace> rows = new LinkedHashMap<>();

        @Override
        public Optional<DriveSpace> findByUserId(UUID userId) {
            return rows.values().stream()
                    .filter(space -> space.userId().equals(userId))
                    .findFirst();
        }

        @Override
        public Optional<DriveSpace> findById(UUID spaceId) {
            return Optional.ofNullable(rows.get(spaceId));
        }

        @Override
        public DriveSpace lockById(UUID spaceId) {
            return rows.get(spaceId);
        }

        @Override
        public boolean reserve(UUID spaceId, long bytes, Instant updatedAt) {
            DriveSpace space = rows.get(spaceId);
            if (space == null || bytes < 0 || space.usedBytes() + space.reservedBytes() + bytes > space.quotaBytes()) {
                return false;
            }
            rows.put(spaceId, space.reserve(bytes, updatedAt));
            return true;
        }

        @Override
        public boolean commitReserved(UUID spaceId, long bytes, Instant updatedAt) {
            DriveSpace space = rows.get(spaceId);
            if (space == null || bytes < 0 || bytes > space.reservedBytes() || space.usedBytes() + bytes > space.quotaBytes()) {
                return false;
            }
            rows.put(spaceId, space.commitReserved(bytes, updatedAt));
            return true;
        }

        @Override
        public boolean releaseReserved(UUID spaceId, long bytes, Instant updatedAt) {
            DriveSpace space = rows.get(spaceId);
            if (space == null || bytes < 0) {
                return false;
            }
            rows.put(spaceId, space.releaseReserved(bytes, updatedAt));
            return true;
        }

        @Override
        public CreateResult create(DriveSpace space) {
            rows.put(space.spaceId(), space);
            return new CreateResult(CreateStatus.CREATED, space);
        }

        @Override
        public void save(DriveSpace space) {
            rows.put(space.spaceId(), space);
        }
    }

    private static final class InMemoryDriveEntryRepository implements DriveEntryRepository {
        private final Map<UUID, DriveEntry> rows = new LinkedHashMap<>();

        @Override
        public Optional<DriveEntry> findById(UUID spaceId, UUID entryId) {
            DriveEntry entry = rows.get(entryId);
            return entry != null && entry.spaceId().equals(spaceId) ? Optional.of(entry) : Optional.empty();
        }

        @Override
        public Optional<DriveEntry> findActiveChildByName(UUID spaceId, UUID parentId, String name) {
            return rows.values().stream()
                    .filter(entry -> entry.spaceId().equals(spaceId))
                    .filter(entry -> Objects.equals(parentId, entry.parentId()))
                    .filter(entry -> entry.status() == DriveEntryStatus.ACTIVE)
                    .filter(entry -> entry.name().equals(name))
                    .findFirst();
        }

        @Override
        public List<DriveEntry> listActiveChildren(UUID spaceId, UUID parentId) {
            return rows.values().stream()
                    .filter(entry -> entry.spaceId().equals(spaceId))
                    .filter(entry -> Objects.equals(parentId, entry.parentId()))
                    .filter(entry -> entry.status() == DriveEntryStatus.ACTIVE)
                    .sorted(entryOrder())
                    .toList();
        }

        @Override
        public List<DriveEntry> listTrash(UUID spaceId) {
            return rows.values().stream()
                    .filter(entry -> entry.spaceId().equals(spaceId))
                    .filter(entry -> entry.status() == DriveEntryStatus.TRASHED)
                    .sorted(Comparator.comparing(DriveEntry::trashedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                    .toList();
        }

        @Override
        public List<DriveEntry> searchActive(UUID spaceId, String keyword, int limit) {
            String normalized = Objects.toString(keyword, "").toLowerCase(Locale.ROOT);
            return rows.values().stream()
                    .filter(entry -> entry.spaceId().equals(spaceId))
                    .filter(entry -> entry.status() == DriveEntryStatus.ACTIVE)
                    .filter(entry -> entry.name().toLowerCase(Locale.ROOT).contains(normalized))
                    .sorted(entryOrder())
                    .limit(limit)
                    .toList();
        }

        @Override
        public List<UUID> listDescendantIds(UUID spaceId, UUID folderId) {
            List<UUID> descendantIds = new ArrayList<>();
            collectDescendants(spaceId, folderId, descendantIds);
            return descendantIds;
        }

        @Override
        public CreateResult create(DriveEntry entry) {
            rows.put(entry.entryId(), entry);
            return new CreateResult(CreateStatus.CREATED, entry);
        }

        @Override
        public boolean markDeletedIfTrashed(DriveEntry deletedEntry) {
            DriveEntry current = rows.get(deletedEntry.entryId());
            if (current == null
                    || !current.spaceId().equals(deletedEntry.spaceId())
                    || current.status() != DriveEntryStatus.TRASHED) {
                return false;
            }
            rows.put(deletedEntry.entryId(), deletedEntry);
            return true;
        }

        @Override
        public void save(DriveEntry entry) {
            rows.put(entry.entryId(), entry);
        }

        private void collectDescendants(UUID spaceId, UUID parentId, List<UUID> descendantIds) {
            rows.values().stream()
                    .filter(entry -> entry.spaceId().equals(spaceId))
                    .filter(entry -> Objects.equals(parentId, entry.parentId()))
                    .forEach(entry -> {
                        descendantIds.add(entry.entryId());
                        if (entry.type() == DriveEntryType.FOLDER) {
                            collectDescendants(spaceId, entry.entryId(), descendantIds);
                        }
                    });
        }

        private static Comparator<DriveEntry> entryOrder() {
            return Comparator.comparing((DriveEntry entry) -> entry.type() == DriveEntryType.FOLDER ? 0 : 1)
                    .thenComparing(DriveEntry::name)
                    .thenComparing(DriveEntry::entryId);
        }
    }

    private static final class InMemoryDriveUploadRepository implements DriveUploadRepository {
        private final Map<UUID, DriveUpload> rows = new LinkedHashMap<>();

        @Override
        public Optional<DriveUpload> findById(UUID uploadId) {
            return Optional.ofNullable(rows.get(uploadId));
        }

        @Override
        public boolean transitionStatus(DriveUpload upload, DriveUploadStatus expectedStatus) {
            DriveUpload current = rows.get(upload.uploadId());
            if (current == null || current.status() != expectedStatus) {
                return false;
            }
            rows.put(upload.uploadId(), upload);
            return true;
        }

        @Override
        public List<DriveUpload> listRecoverableBefore(Instant updatedBefore, int limit) {
            if (updatedBefore == null || limit <= 0) {
                return List.of();
            }
            return rows.values().stream()
                    .filter(upload -> upload.status() == DriveUploadStatus.COMPLETING
                            || upload.status() == DriveUploadStatus.OBJECT_COMPLETED)
                    .filter(upload -> upload.updatedAt().isBefore(updatedBefore))
                    .sorted(Comparator.comparing(DriveUpload::updatedAt).thenComparing(DriveUpload::uploadId))
                    .limit(limit)
                    .toList();
        }

        @Override
        public void save(DriveUpload upload) {
            rows.put(upload.uploadId(), upload);
        }
    }

    private static final class InMemoryDriveShareRepository implements DriveShareRepository {
        private final Map<UUID, DriveShare> rows = new LinkedHashMap<>();

        @Override
        public Optional<DriveShare> findById(UUID shareId) {
            return Optional.ofNullable(rows.get(shareId));
        }

        @Override
        public Optional<DriveShare> findByToken(String shareToken) {
            return rows.values().stream()
                    .filter(share -> share.shareToken().equals(shareToken))
                    .findFirst();
        }

        @Override
        public Optional<DriveShare> findActiveByEntryId(UUID entryId) {
            return rows.values().stream()
                    .filter(share -> share.entryId().equals(entryId))
                    .filter(share -> share.status().name().equals("ACTIVE"))
                    .findFirst();
        }

        @Override
        public void save(DriveShare share) {
            rows.put(share.shareId(), share);
        }
    }

    static final class InMemoryDriveShareAccessRepository implements DriveShareAccessRepository {
        private final List<AccessRecord> records = new ArrayList<>();

        @Override
        public void record(UUID accessId, UUID shareId, String visitorFingerprint, boolean success, Instant accessedAt) {
            records.add(new AccessRecord(accessId, shareId, visitorFingerprint, success, accessedAt));
        }

        List<AccessRecord> records() {
            return records;
        }
    }

    static final class FakeStoragePort implements DriveObjectStoragePort {
        final List<PrepareObject> prepared = new ArrayList<>();
        final List<CompleteObject> completed = new ArrayList<>();
        final List<UUID> downloadObjectIds = new ArrayList<>();
        final List<UUID> deletedObjects = new ArrayList<>();

        @Override
        public PreparedObject prepareUpload(PrepareObject command) {
            prepared.add(command);
            int suffix = 100 + prepared.size();
            return new PreparedObject(uuid(suffix), uuid(suffix + 100), uuid(suffix + 200), NOW.plusSeconds(900));
        }

        @Override
        public StoredObject completeUpload(CompleteObject command) {
            completed.add(command);
            return new StoredObject(command.objectId(), command.versionId(), "");
        }

        @Override
        public ObjectMetadata getMetadata(UUID objectId) {
            return completed.stream()
                    .filter(command -> command.objectId().equals(objectId))
                    .findFirst()
                    .map(command -> new ObjectMetadata(
                            command.objectId(),
                            command.versionId(),
                            "ACTIVE",
                            command.fileName(),
                            command.contentType(),
                            command.contentLength(),
                            command.checksumSha256(),
                            ""
                    ))
                    .orElse(null);
        }

        @Override
        public SignedDownloadUrl createDownloadUrl(UUID objectId, long ttlSeconds) {
            downloadObjectIds.add(objectId);
            return new SignedDownloadUrl("https://cdn.example.test/" + objectId, NOW.plusSeconds(ttlSeconds));
        }

        @Override
        public void deleteObject(UUID objectId, String actorId) {
            deletedObjects.add(objectId);
        }
    }

    private static final class FakePasswordHasher implements DrivePasswordHasher {
        @Override
        public String hash(String rawPassword) {
            return "hashed:" + rawPassword;
        }

        @Override
        public boolean matches(String rawPassword, String passwordHash) {
            return Objects.equals(hash(rawPassword), passwordHash);
        }
    }

    static final class FakeTicketCodec implements DriveShareTicketCodec {
        private final List<String> issued = new ArrayList<>();

        @Override
        public String issue(String shareToken, Instant expiresAt) {
            String ticket = shareToken + ":" + expiresAt.getEpochSecond();
            issued.add(ticket);
            return ticket;
        }

        @Override
        public boolean valid(String shareToken, String ticket, Instant now) {
            String[] parts = Objects.toString(ticket, "").split(":");
            if (parts.length != 2 || !parts[0].equals(shareToken)) {
                return false;
            }
            try {
                return now.isBefore(Instant.ofEpochSecond(Long.parseLong(parts[1])));
            } catch (NumberFormatException e) {
                return false;
            }
        }

        List<String> issued() {
            return issued;
        }
    }

    record AccessRecord(UUID accessId, UUID shareId, String visitorFingerprint, boolean success, Instant accessedAt) {
    }
}
