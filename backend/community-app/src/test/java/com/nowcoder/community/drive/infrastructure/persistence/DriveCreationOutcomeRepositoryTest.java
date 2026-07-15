package com.nowcoder.community.drive.infrastructure.persistence;

import com.nowcoder.community.drive.domain.model.DriveEntry;
import com.nowcoder.community.drive.domain.model.DriveSpace;
import com.nowcoder.community.drive.domain.repository.DriveEntryRepository;
import com.nowcoder.community.drive.domain.repository.DriveSpaceRepository;
import com.nowcoder.community.drive.infrastructure.persistence.dataobject.DriveEntryDataObject;
import com.nowcoder.community.drive.infrastructure.persistence.dataobject.DriveSpaceDataObject;
import com.nowcoder.community.drive.infrastructure.persistence.mapper.DriveEntryMapper;
import com.nowcoder.community.drive.infrastructure.persistence.mapper.DriveSpaceMapper;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DriveCreationOutcomeRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-07-15T00:00:00Z");
    private static final UUID USER_ID = uuid(1);
    private static final UUID SPACE_ID = uuid(2);

    @Test
    void spaceCreateShouldReloadConcurrentOwnerDuplicate() {
        DriveSpaceMapper mapper = mock(DriveSpaceMapper.class);
        DriveSpace candidate = DriveSpace.createDefault(uuid(3), USER_ID, NOW);
        DriveSpace existing = DriveSpace.createDefault(SPACE_ID, USER_ID, NOW.minusSeconds(1));
        when(mapper.insert(any())).thenThrow(new DuplicateKeyException("uk_drive_space_user"));
        when(mapper.selectByUserId(USER_ID)).thenReturn(DriveSpaceDataObject.fromDomain(existing));

        DriveSpaceRepository.CreateResult result = new MyBatisDriveSpaceRepository(mapper).create(candidate);

        assertThat(result.status()).isEqualTo(DriveSpaceRepository.CreateStatus.ALREADY_EXISTS);
        assertThat(result.space()).isEqualTo(existing);
        verify(mapper).selectByUserId(USER_ID);
    }

    @Test
    void spaceCreateShouldNotTreatUnknownIntegrityFailureAsReplay() {
        DriveSpaceMapper mapper = mock(DriveSpaceMapper.class);
        DriveSpace candidate = DriveSpace.createDefault(SPACE_ID, USER_ID, NOW);
        DataIntegrityViolationException failure = new DataIntegrityViolationException("unknown constraint");
        when(mapper.insert(any())).thenThrow(failure);
        when(mapper.selectByUserId(USER_ID)).thenReturn(DriveSpaceDataObject.fromDomain(candidate));

        assertThatThrownBy(() -> new MyBatisDriveSpaceRepository(mapper).create(candidate))
                .isSameAs(failure);
        verify(mapper, never()).selectByUserId(USER_ID);
    }

    @Test
    void entryCreateShouldReloadSameSemanticEntryAsReplay() {
        DriveEntryMapper mapper = mock(DriveEntryMapper.class);
        DriveEntry candidate = DriveEntry.folder(uuid(11), SPACE_ID, null, "Docs", NOW);
        when(mapper.insert(any())).thenThrow(new DuplicateKeyException("drive_entry.PRIMARY"));
        when(mapper.selectById(SPACE_ID, candidate.entryId()))
                .thenReturn(DriveEntryDataObject.fromDomain(candidate));

        DriveEntryRepository.CreateResult result = new MyBatisDriveEntryRepository(mapper).create(candidate);

        assertThat(result.status()).isEqualTo(DriveEntryRepository.CreateStatus.ALREADY_EXISTS);
        assertThat(result.entry()).isEqualTo(candidate);
    }

    @Test
    void entryCreateShouldDistinguishActiveNameConflictFromUnknownConflict() {
        DriveEntryMapper mapper = mock(DriveEntryMapper.class);
        DriveEntry candidate = DriveEntry.folder(uuid(11), SPACE_ID, null, "Docs", NOW);
        DriveEntry competing = DriveEntry.folder(uuid(12), SPACE_ID, null, "Docs", NOW.minusSeconds(1));
        when(mapper.insert(any())).thenThrow(new DuplicateKeyException("uk_drive_entry_active_name"));
        when(mapper.selectById(SPACE_ID, candidate.entryId())).thenReturn(null);
        when(mapper.selectActiveChildByName(SPACE_ID, null, "", "Docs"))
                .thenReturn(DriveEntryDataObject.fromDomain(competing));

        DriveEntryRepository.CreateResult nameConflict = new MyBatisDriveEntryRepository(mapper).create(candidate);

        assertThat(nameConflict.status()).isEqualTo(DriveEntryRepository.CreateStatus.ACTIVE_NAME_CONFLICT);
        assertThat(nameConflict.entry()).isEqualTo(competing);

        DriveEntryMapper unknownMapper = mock(DriveEntryMapper.class);
        DataIntegrityViolationException failure = new DataIntegrityViolationException("unknown constraint");
        when(unknownMapper.insert(any())).thenThrow(failure);
        when(unknownMapper.selectById(SPACE_ID, candidate.entryId()))
                .thenReturn(DriveEntryDataObject.fromDomain(candidate));

        assertThatThrownBy(() -> new MyBatisDriveEntryRepository(unknownMapper).create(candidate))
                .isSameAs(failure);
        verify(unknownMapper, never()).selectById(SPACE_ID, candidate.entryId());
    }

    @Test
    void entryCreateShouldIgnoreDatabaseTimestampPrecisionWhenValidatingReplay() {
        DriveEntryMapper mapper = mock(DriveEntryMapper.class);
        DriveEntry candidate = DriveEntry.folder(uuid(11), SPACE_ID, null, "Docs", NOW.plusNanos(123_456_789));
        DriveEntry persisted = DriveEntry.folder(uuid(11), SPACE_ID, null, "Docs", NOW.plusMillis(123));
        when(mapper.insert(any())).thenThrow(new DuplicateKeyException("drive_entry.PRIMARY"));
        when(mapper.selectById(SPACE_ID, candidate.entryId()))
                .thenReturn(DriveEntryDataObject.fromDomain(persisted));

        DriveEntryRepository.CreateResult result = new MyBatisDriveEntryRepository(mapper).create(candidate);

        assertThat(result.status()).isEqualTo(DriveEntryRepository.CreateStatus.ALREADY_EXISTS);
        assertThat(result.entry()).isEqualTo(persisted);
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }
}
