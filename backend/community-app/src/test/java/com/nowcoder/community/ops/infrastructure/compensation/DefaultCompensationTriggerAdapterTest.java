package com.nowcoder.community.ops.infrastructure.compensation;

import com.nowcoder.community.ops.application.OutboxLeaseRecoveryPort;
import com.nowcoder.community.ops.application.command.TriggerCompensationCommand;
import com.nowcoder.community.ops.application.result.OutboxLeaseRecoveryResult;
import com.nowcoder.community.ops.domain.model.GovernanceResult;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultCompensationTriggerAdapterTest {

    @Test
    void triggerShouldRecoverExpiredOutboxLeasesWithBoundedLimit() {
        OutboxLeaseRecoveryPort outboxLeaseRecoveryPort = mock(OutboxLeaseRecoveryPort.class);
        when(outboxLeaseRecoveryPort.recoverExpiredLeases(25))
                .thenReturn(new OutboxLeaseRecoveryResult(25, 7));
        DefaultCompensationTriggerAdapter adapter = new DefaultCompensationTriggerAdapter(outboxLeaseRecoveryPort);

        var result = adapter.trigger(new TriggerCompensationCommand(
                uuid(99),
                "outboxRecoverExpiredLeases",
                25,
                "recover expired workers"
        ));

        assertThat(result.jobName()).isEqualTo("outboxRecoverExpiredLeases");
        assertThat(result.accepted()).isTrue();
        assertThat(result.processedCount()).isEqualTo(25);
        assertThat(result.repairedCount()).isEqualTo(7);
        assertThat(result.skippedCount()).isEqualTo(18);
        assertThat(result.result()).isEqualTo(GovernanceResult.ACCEPTED.name());
        verify(outboxLeaseRecoveryPort).recoverExpiredLeases(25);
    }

    @Test
    void triggerShouldSkipProjectionRepairWhenOwnerTriggerIsUnavailable() {
        OutboxLeaseRecoveryPort outboxLeaseRecoveryPort = mock(OutboxLeaseRecoveryPort.class);
        DefaultCompensationTriggerAdapter adapter = new DefaultCompensationTriggerAdapter(outboxLeaseRecoveryPort);

        var result = adapter.trigger(new TriggerCompensationCommand(
                uuid(99),
                "searchPostProjectionRepair",
                20,
                "repair projection"
        ));

        assertThat(result.jobName()).isEqualTo("searchPostProjectionRepair");
        assertThat(result.accepted()).isFalse();
        assertThat(result.processedCount()).isZero();
        assertThat(result.repairedCount()).isZero();
        assertThat(result.skippedCount()).isEqualTo(20);
        assertThat(result.result()).isEqualTo(GovernanceResult.SKIPPED.name());
        assertThat(result.message()).contains("owner repair trigger is not configured");
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }
}
