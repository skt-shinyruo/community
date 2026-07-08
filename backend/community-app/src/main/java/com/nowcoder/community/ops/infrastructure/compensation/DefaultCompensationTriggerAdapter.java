package com.nowcoder.community.ops.infrastructure.compensation;

import com.nowcoder.community.ops.application.CompensationTriggerPort;
import com.nowcoder.community.ops.application.OutboxLeaseRecoveryPort;
import com.nowcoder.community.ops.application.command.TriggerCompensationCommand;
import com.nowcoder.community.ops.application.result.CompensationTriggerResult;
import com.nowcoder.community.ops.application.result.OutboxLeaseRecoveryResult;
import com.nowcoder.community.ops.domain.model.GovernanceResult;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class DefaultCompensationTriggerAdapter implements CompensationTriggerPort {

    private static final String OUTBOX_RECOVER_EXPIRED_LEASES = "outboxRecoverExpiredLeases";

    private final OutboxLeaseRecoveryPort outboxLeaseRecoveryPort;

    public DefaultCompensationTriggerAdapter(OutboxLeaseRecoveryPort outboxLeaseRecoveryPort) {
        this.outboxLeaseRecoveryPort = Objects.requireNonNull(outboxLeaseRecoveryPort, "outboxLeaseRecoveryPort must not be null");
    }

    @Override
    public CompensationTriggerResult trigger(TriggerCompensationCommand command) {
        if (OUTBOX_RECOVER_EXPIRED_LEASES.equals(command.jobName())) {
            return recoverOutboxLeases(command);
        }
        return new CompensationTriggerResult(
                command.jobName(),
                false,
                0,
                0,
                command.limit(),
                GovernanceResult.SKIPPED.name(),
                "owner repair trigger is not configured for job=" + command.jobName()
        );
    }

    private CompensationTriggerResult recoverOutboxLeases(TriggerCompensationCommand command) {
        OutboxLeaseRecoveryResult recovery = outboxLeaseRecoveryPort.recoverExpiredLeases(command.limit());
        int skipped = Math.max(0, recovery.selectedCount() - recovery.recoveredCount());
        String result = recovery.recoveredCount() > 0 ? GovernanceResult.ACCEPTED.name() : GovernanceResult.SKIPPED.name();
        return new CompensationTriggerResult(
                command.jobName(),
                recovery.recoveredCount() > 0,
                recovery.selectedCount(),
                recovery.recoveredCount(),
                skipped,
                result,
                "expired outbox leases recovered"
        );
    }
}
