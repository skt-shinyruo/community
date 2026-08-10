package com.nowcoder.community.ops.application;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.ops.application.command.RecordGovernanceAuditCommand;
import com.nowcoder.community.ops.application.result.OutboxLeaseRecoveryResult;
import com.nowcoder.community.ops.domain.model.GovernanceResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CompensationGovernanceApplicationServiceTest {

    private OutboxLeaseRecoveryPort outboxLeaseRecoveryPort;
    private GovernanceMetrics governanceMetrics;
    private GovernanceAuditPort auditPort;
    private CompensationGovernanceApplicationService service;

    @BeforeEach
    void setUp() {
        outboxLeaseRecoveryPort = mock(OutboxLeaseRecoveryPort.class);
        governanceMetrics = mock(GovernanceMetrics.class);
        auditPort = mock(GovernanceAuditPort.class);
        service = new CompensationGovernanceApplicationService(outboxLeaseRecoveryPort, governanceMetrics, auditPort);
    }

    @Test
    void triggerShouldRejectUnknownJob() {
        assertThatThrownBy(() -> service.trigger(new CompensationGovernanceApplicationService.TriggerCommand(
                uuid(99),
                "arbitrarySpringBean",
                10,
                "retry"
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("compensation job is not allow-listed");
    }

    @Test
    void triggerShouldRequireReasonAndBoundedLimit() {
        assertThatThrownBy(() -> service.trigger(new CompensationGovernanceApplicationService.TriggerCommand(
                uuid(99),
                "outboxRecoverExpiredLeases",
                10,
                " "
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("compensation reason is required");
        assertThatThrownBy(() -> service.trigger(new CompensationGovernanceApplicationService.TriggerCommand(
                uuid(99),
                "outboxRecoverExpiredLeases",
                0,
                "retry"
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("limit must be between 1 and 500");
        assertThatThrownBy(() -> service.trigger(new CompensationGovernanceApplicationService.TriggerCommand(
                uuid(99),
                "outboxRecoverExpiredLeases",
                501,
                "retry"
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("limit must be between 1 and 500");
    }

    @Test
    void triggerShouldSkipKnownProjectionRepairWhenOwnerTriggerIsUnavailable() {
        UUID actorId = uuid(99);
        CompensationGovernanceApplicationService.TriggerResult result = service.trigger(
                new CompensationGovernanceApplicationService.TriggerCommand(
                actorId,
                " searchPostProjectionRepair ",
                20,
                " repair stale projection "
        ));

        assertThat(result.jobName()).isEqualTo("searchPostProjectionRepair");
        assertThat(result.accepted()).isFalse();
        assertThat(result.processedCount()).isZero();
        assertThat(result.repairedCount()).isZero();
        assertThat(result.skippedCount()).isEqualTo(20);
        assertThat(result.result()).isEqualTo(GovernanceResult.SKIPPED.name());
        assertThat(result.message()).contains("owner repair trigger is not configured");
        verify(outboxLeaseRecoveryPort, never()).recoverExpiredLeases(anyInt());
    }

    @Test
    void triggerShouldRecordAuditAndMetricsOnAcceptedResult() {
        UUID actorId = uuid(99);
        when(outboxLeaseRecoveryPort.recoverExpiredLeases(10))
                .thenReturn(new OutboxLeaseRecoveryResult(10, 8));

        CompensationGovernanceApplicationService.TriggerResult result = service.trigger(
                new CompensationGovernanceApplicationService.TriggerCommand(
                        actorId,
                        "outboxRecoverExpiredLeases",
                        10,
                        "recover expired workers"
                ));

        assertThat(result.processedCount()).isEqualTo(10);
        assertThat(result.repairedCount()).isEqualTo(8);
        assertThat(result.skippedCount()).isEqualTo(2);
        verify(governanceMetrics).recordCompensationTrigger("outboxRecoverExpiredLeases", GovernanceResult.ACCEPTED.name());
        verify(governanceMetrics).recordGovernanceAction("COMPENSATION_TRIGGER", GovernanceResult.ACCEPTED.name());
        verify(auditPort).record(any(RecordGovernanceAuditCommand.class));
    }

    @Test
    void triggerShouldRecordFailedMetricAndAuditWhenPortFails() {
        UUID actorId = uuid(99);
        when(outboxLeaseRecoveryPort.recoverExpiredLeases(10))
                .thenThrow(new IllegalStateException("lease recovery failed"));

        CompensationGovernanceApplicationService.TriggerResult result = service.trigger(
                new CompensationGovernanceApplicationService.TriggerCommand(
                actorId,
                "outboxRecoverExpiredLeases",
                10,
                "recover expired workers"
        ));

        assertThat(result.jobName()).isEqualTo("outboxRecoverExpiredLeases");
        assertThat(result.accepted()).isFalse();
        assertThat(result.result()).isEqualTo(GovernanceResult.FAILED.name());
        assertThat(result.message()).contains("lease recovery failed");
        verify(governanceMetrics).recordCompensationTrigger("outboxRecoverExpiredLeases", GovernanceResult.FAILED.name());
        verify(governanceMetrics).recordGovernanceAction("COMPENSATION_TRIGGER", GovernanceResult.FAILED.name());
        verify(auditPort).record(any(RecordGovernanceAuditCommand.class));
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }
}
