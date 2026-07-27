package com.nowcoder.community.oss.application;

import com.nowcoder.community.oss.application.port.ObjectDeletePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class ObjectDeletionRecoveryApplicationService {

    private static final Logger log = LoggerFactory.getLogger(ObjectDeletionRecoveryApplicationService.class);

    private final ObjectLifecycleTransactionOperations transactionOperations;
    private final ObjectDeletePort deletePort;
    private final Clock clock;

    public ObjectDeletionRecoveryApplicationService(
            ObjectLifecycleTransactionOperations transactionOperations,
            ObjectDeletePort deletePort,
            Clock clock
    ) {
        this.transactionOperations = Objects.requireNonNull(
                transactionOperations, "transactionOperations must not be null");
        this.deletePort = Objects.requireNonNull(deletePort, "deletePort must not be null");
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    public void recoverPendingDeletions(Instant updatedBefore, int limit) {
        List<UUID> objectIds = transactionOperations.listRecoverableDeletionIds(updatedBefore, limit);
        for (UUID objectId : objectIds) {
            try {
                transactionOperations.claimRecoverableDeletion(objectId, clock.instant())
                        .ifPresent(this::deleteAndFinalize);
            } catch (RuntimeException failure) {
                log.warn("[oss-delete] failed to recover object {}: {}", objectId, failure.toString());
            }
        }
    }

    private void deleteAndFinalize(ObjectDeletionClaim claim) {
        deletePort.deleteIfExists(claim.storageBucket(), claim.storageKey());
        transactionOperations.finalizeDeletion(claim, clock.instant());
    }
}
