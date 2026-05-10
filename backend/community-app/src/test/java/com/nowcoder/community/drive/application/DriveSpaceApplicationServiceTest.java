package com.nowcoder.community.drive.application;

import com.nowcoder.community.drive.application.result.DriveSpaceResult;
import com.nowcoder.community.drive.domain.model.DriveSpace;
import com.nowcoder.community.drive.domain.repository.DriveSpaceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DriveSpaceApplicationServiceTest {

    @Test
    void getSpaceShouldRecoverFromDuplicateKeyDuringBootstrap() {
        DriveSpaceRepository spaceRepository = mock(DriveSpaceRepository.class);
        Instant now = Instant.parse("2026-05-09T00:00:00Z");
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        UUID userId = uuid(7);
        UUID spaceId = uuid(8);
        DriveSpace persisted = DriveSpace.createDefault(spaceId, userId, now);

        when(spaceRepository.findByUserId(userId))
                .thenReturn(Optional.empty(), Optional.of(persisted));
        doThrow(new DuplicateKeyException("duplicate drive_space user")).when(spaceRepository).save(any(DriveSpace.class));

        DriveSpaceApplicationService service = new DriveSpaceApplicationService(spaceRepository, clock);
        DriveSpaceResult result = service.getSpace(userId);

        assertThat(result.spaceId()).isEqualTo(spaceId);
        assertThat(result.quotaBytes()).isEqualTo(10_737_418_240L);
        assertThat(result.usedBytes()).isZero();
        verify(spaceRepository, times(2)).findByUserId(userId);
        verify(spaceRepository).save(any(DriveSpace.class));
    }
}
