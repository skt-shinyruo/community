package com.nowcoder.community.drive.infrastructure.persistence;

import com.nowcoder.community.app.CommunityAppApplication;
import com.nowcoder.community.drive.domain.model.DriveEntry;
import com.nowcoder.community.drive.domain.model.DriveEntryStatus;
import com.nowcoder.community.drive.domain.repository.DriveEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = CommunityAppApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@ActiveProfiles("test")
class MyBatisDriveEntryRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-05-09T00:00:00Z");
    private static final UUID SPACE_ID = uuid(80);

    @Autowired
    private DriveEntryRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from drive_entry");
    }

    @Test
    void markDeletedIfTrashedShouldLetOnlyTheFirstTransitionWin() {
        DriveEntry trashed = file(uuid(81), uuid(82)).trash(NOW, NOW.plusSeconds(86_400));
        DriveEntry deleted = trashed.delete(NOW.plusSeconds(1));
        repository.save(trashed);

        boolean first = repository.markDeletedIfTrashed(deleted);
        boolean second = repository.markDeletedIfTrashed(deleted);

        assertThat(first).isTrue();
        assertThat(second).isFalse();
        assertThat(repository.findById(SPACE_ID, trashed.entryId())).contains(deleted);
    }

    @Test
    void markDeletedIfTrashedShouldRejectActiveDeletedAndMissingRowsWithoutInserting() {
        DriveEntry active = file(uuid(83), uuid(84));
        DriveEntry alreadyDeleted = file(uuid(85), uuid(86))
                .trash(NOW, NOW.plusSeconds(86_400))
                .delete(NOW.plusSeconds(1));
        DriveEntry missingDeleted = file(uuid(87), uuid(88))
                .trash(NOW, NOW.plusSeconds(86_400))
                .delete(NOW.plusSeconds(1));
        repository.save(active);
        repository.save(alreadyDeleted);

        assertThat(repository.markDeletedIfTrashed(active.delete(NOW.plusSeconds(1)))).isFalse();
        assertThat(repository.markDeletedIfTrashed(alreadyDeleted)).isFalse();
        assertThat(repository.markDeletedIfTrashed(missingDeleted)).isFalse();

        assertThat(repository.findById(SPACE_ID, active.entryId()).orElseThrow().status())
                .isEqualTo(DriveEntryStatus.ACTIVE);
        assertThat(repository.findById(SPACE_ID, alreadyDeleted.entryId())).contains(alreadyDeleted);
        assertThat(repository.findById(SPACE_ID, missingDeleted.entryId())).isEmpty();
        assertThat(jdbcTemplate.queryForObject("select count(*) from drive_entry", Integer.class)).isEqualTo(2);
    }

    private static DriveEntry file(UUID entryId, UUID objectId) {
        return DriveEntry.file(
                entryId,
                SPACE_ID,
                null,
                "file-" + entryId,
                objectId,
                objectId,
                8,
                "text/plain",
                NOW
        );
    }
}
