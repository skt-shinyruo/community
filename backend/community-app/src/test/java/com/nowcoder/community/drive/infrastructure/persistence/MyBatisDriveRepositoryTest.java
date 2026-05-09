package com.nowcoder.community.drive.infrastructure.persistence;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.common.id.BinaryUuidCodec;
import com.nowcoder.community.common.web.net.ClientIpResolver;
import com.nowcoder.community.drive.domain.model.DriveEntry;
import com.nowcoder.community.drive.domain.model.DriveEntryStatus;
import com.nowcoder.community.drive.domain.model.DriveShare;
import com.nowcoder.community.drive.domain.model.DriveShareStatus;
import com.nowcoder.community.drive.domain.model.DriveSpace;
import com.nowcoder.community.drive.domain.model.DriveUpload;
import com.nowcoder.community.drive.domain.model.DriveUploadStatus;
import com.nowcoder.community.drive.domain.repository.DriveEntryRepository;
import com.nowcoder.community.drive.domain.repository.DriveShareAccessRepository;
import com.nowcoder.community.drive.domain.repository.DriveShareRepository;
import com.nowcoder.community.drive.domain.repository.DriveSpaceRepository;
import com.nowcoder.community.drive.domain.repository.DriveUploadRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = CommunityAppApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@ActiveProfiles("test")
class MyBatisDriveRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-05-09T10:15:30Z");
    private static final UUID SPACE_ID = uuid(1);
    private static final UUID USER_ID = uuid(2);
    private static final UUID ROOT_ID = uuid(3);
    private static final UUID FOLDER_ID = uuid(4);
    private static final UUID FILE_ID = uuid(5);
    private static final UUID NESTED_ID = uuid(6);
    private static final UUID UPLOAD_ID = uuid(7);
    private static final UUID SHARE_ID = uuid(8);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DriveSpaceRepository spaceRepository;

    @Autowired
    private DriveEntryRepository entryRepository;

    @Autowired
    private DriveUploadRepository uploadRepository;

    @Autowired
    private DriveShareRepository shareRepository;

    @Autowired
    private DriveShareAccessRepository shareAccessRepository;

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
    void driveSpaceRepositoryShouldInsertUpdateAndFindByIds() {
        DriveSpace created = DriveSpace.createDefault(SPACE_ID, USER_ID, NOW);

        spaceRepository.save(created);
        DriveSpace reserved = created.reserve(128L, NOW.plusSeconds(10));
        spaceRepository.save(reserved);

        assertThat(spaceRepository.findById(SPACE_ID)).contains(reserved);
        assertThat(spaceRepository.findByUserId(USER_ID)).contains(reserved);
    }

    @Test
    void driveEntryRepositoryShouldSaveFindListSearchTrashAndDescendants() {
        spaceRepository.save(DriveSpace.createDefault(SPACE_ID, USER_ID, NOW));
        DriveEntry root = DriveEntry.folder(ROOT_ID, SPACE_ID, null, "root", NOW);
        DriveEntry folder = DriveEntry.folder(FOLDER_ID, SPACE_ID, ROOT_ID, "Projects", NOW.plusSeconds(1));
        DriveEntry file = DriveEntry.file(FILE_ID, SPACE_ID, FOLDER_ID, "Plan.txt", uuid(20), uuid(21), 42L, "text/plain", NOW.plusSeconds(2));
        DriveEntry nested = DriveEntry.file(NESTED_ID, SPACE_ID, FILE_ID, "Nested.txt", uuid(22), uuid(23), 10L, "text/plain", NOW.plusSeconds(3));

        entryRepository.save(root);
        entryRepository.save(folder);
        entryRepository.save(file);
        entryRepository.save(nested);
        DriveEntry renamed = file.rename("Roadmap.txt", NOW.plusSeconds(4));
        entryRepository.save(renamed);
        DriveEntry trashed = nested.trash(NOW.plusSeconds(5), NOW.plusSeconds(86400));
        entryRepository.save(trashed);

        assertThat(entryRepository.findById(SPACE_ID, FILE_ID)).contains(renamed);
        assertThat(entryRepository.findActiveChildByName(SPACE_ID, FOLDER_ID, "Roadmap.txt")).contains(renamed);
        assertThat(entryRepository.listActiveChildren(SPACE_ID, FOLDER_ID)).containsExactly(renamed);
        assertThat(entryRepository.searchActive(SPACE_ID, "road", 10)).containsExactly(renamed);
        assertThat(entryRepository.listTrash(SPACE_ID)).containsExactly(trashed);
        assertThat(entryRepository.listDescendantIds(SPACE_ID, ROOT_ID)).containsExactly(FOLDER_ID, FILE_ID, NESTED_ID);

        String activeName = jdbcTemplate.queryForObject(
                "select active_name from drive_entry where entry_id = ?",
                String.class,
                BinaryUuidCodec.toBytes(FILE_ID)
        );
        String trashedActiveName = jdbcTemplate.queryForObject(
                "select active_name from drive_entry where entry_id = ?",
                String.class,
                BinaryUuidCodec.toBytes(NESTED_ID)
        );
        assertThat(activeName).isEqualTo("Roadmap.txt");
        assertThat(trashedActiveName).isNull();
    }

    @Test
    void driveUploadRepositoryShouldInsertUpdateAndFind() {
        DriveUpload prepared = DriveUpload.prepared(
                UPLOAD_ID,
                SPACE_ID,
                ROOT_ID,
                "upload.bin",
                99L,
                "application/octet-stream",
                uuid(30),
                uuid(31),
                uuid(32),
                USER_ID,
                NOW,
                NOW.plusSeconds(3600)
        );

        uploadRepository.save(prepared);
        DriveUpload completed = prepared.complete(FILE_ID, NOW.plusSeconds(20));
        uploadRepository.save(completed);

        DriveUpload persisted = uploadRepository.findById(UPLOAD_ID).orElseThrow();
        assertThat(persisted).extracting(
                DriveUpload::uploadId,
                DriveUpload::spaceId,
                DriveUpload::parentId,
                DriveUpload::name,
                DriveUpload::status,
                DriveUpload::completedEntryId
        ).containsExactly(UPLOAD_ID, SPACE_ID, ROOT_ID, "upload.bin", DriveUploadStatus.COMPLETED, FILE_ID);
    }

    @Test
    void driveShareRepositoryShouldInsertUpdateAndFindByTokenAndActiveEntry() {
        DriveShare active = DriveShare.active(
                SHARE_ID,
                FILE_ID,
                "share-token",
                "password-hash",
                NOW.plusSeconds(3600),
                USER_ID,
                NOW
        );

        shareRepository.save(active);
        DriveShare revoked = active.revoke(NOW.plusSeconds(30));
        shareRepository.save(revoked);

        DriveShare persisted = shareRepository.findById(SHARE_ID).orElseThrow();
        assertThat(persisted).extracting(
                DriveShare::shareId,
                DriveShare::entryId,
                DriveShare::shareToken,
                DriveShare::status
        ).containsExactly(SHARE_ID, FILE_ID, "share-token", DriveShareStatus.REVOKED);
        assertThat(shareRepository.findByToken("share-token").map(DriveShare::status)).contains(DriveShareStatus.REVOKED);
        assertThat(shareRepository.findActiveByEntryId(FILE_ID)).isEmpty();
    }

    @Test
    void driveShareAccessRepositoryShouldRecordAccessAttempt() {
        UUID accessId = uuid(40);

        shareAccessRepository.record(accessId, SHARE_ID, "visitor-1", true, NOW);

        Boolean success = jdbcTemplate.queryForObject(
                "select success from drive_share_access where access_id = ?",
                Boolean.class,
                BinaryUuidCodec.toBytes(accessId)
        );
        Timestamp accessedAt = jdbcTemplate.queryForObject(
                "select accessed_at from drive_share_access where access_id = ?",
                Timestamp.class,
                BinaryUuidCodec.toBytes(accessId)
        );
        assertThat(success).isTrue();
        assertThat(accessedAt.toInstant()).isEqualTo(NOW);
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }
}
