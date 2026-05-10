package com.nowcoder.community.drive.application;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.drive.application.result.DriveSpaceResult;
import com.nowcoder.community.drive.domain.model.DriveSpace;
import com.nowcoder.community.drive.domain.repository.DriveSpaceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;

@Service
public class DriveSpaceApplicationService {

    private final DriveSpaceRepository spaceRepository;
    private final Clock clock;

    public DriveSpaceApplicationService(DriveSpaceRepository spaceRepository, Clock clock) {
        this.spaceRepository = spaceRepository;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    @Transactional
    public DriveSpaceResult getSpace(UUID actorUserId) {
        UUID userId = requireUser(actorUserId);
        Instant now = clock.instant();
        DriveSpace space = spaceRepository.findByUserId(userId)
                .orElseGet(() -> createDefaultSpace(userId, now));
        return toResult(space);
    }

    private DriveSpace createDefaultSpace(UUID userId, Instant now) {
        DriveSpace space = DriveSpace.createDefault(UUID.randomUUID(), userId, now);
        spaceRepository.save(space);
        return space;
    }

    private static DriveSpaceResult toResult(DriveSpace space) {
        return new DriveSpaceResult(
                space.spaceId(),
                space.userId(),
                space.quotaBytes(),
                space.usedBytes(),
                space.remainingBytes()
        );
    }

    private static UUID requireUser(UUID actorUserId) {
        if (actorUserId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "actorUserId 非法");
        }
        return actorUserId;
    }
}
