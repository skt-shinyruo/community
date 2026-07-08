package com.nowcoder.community.ops.application;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.ops.application.command.RecordGovernanceAuditCommand;
import com.nowcoder.community.ops.application.command.TriggerCompensationCommand;
import com.nowcoder.community.ops.application.result.CompensationTriggerResult;
import com.nowcoder.community.ops.domain.model.GovernanceResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CompensationGovernanceApplicationServiceTest {

    private CompensationTriggerPort triggerPort;
    private GovernanceMetrics governanceMetrics;
    private GovernanceAuditPort auditPort;
    private CompensationGovernanceApplicationService service;

    @BeforeEach
    void setUp() {
        triggerPort = mock(CompensationTriggerPort.class);
        governanceMetrics = mock(GovernanceMetrics.class);
        auditPort = mock(GovernanceAuditPort.class);
        service = new CompensationGovernanceApplicationService(triggerPort, governanceMetrics, auditPort);
    }

    @Test
    void triggerShouldRejectUnknownJob() {
        assertThatThrownBy(() -> service.trigger(new TriggerCompensationCommand(
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
        assertThatThrownBy(() -> service.trigger(new TriggerCompensationCommand(
                uuid(99),
                "outboxRecoverExpiredLeases",
                10,
                " "
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("compensation reason is required");
        assertThatThrownBy(() -> service.trigger(new TriggerCompensationCommand(
                uuid(99),
                "outboxRecoverExpiredLeases",
                0,
                "retry"
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("limit must be between 1 and 500");
        assertThatThrownBy(() -> service.trigger(new TriggerCompensationCommand(
                uuid(99),
                "outboxRecoverExpiredLeases",
                501,
                "retry"
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("limit must be between 1 and 500");
    }

    @Test
    void triggerShouldDelegateKnownJobToPort() {
        UUID actorId = uuid(99);
        when(triggerPort.trigger(new TriggerCompensationCommand(
                actorId,
                "searchPostProjectionRepair",
                20,
                "repair stale projection"
        ))).thenReturn(new CompensationTriggerResult(
                "searchPostProjectionRepair",
                true,
                20,
                18,
                2,
                GovernanceResult.ACCEPTED.name(),
                "accepted"
        ));

        CompensationTriggerResult result = service.trigger(new TriggerCompensationCommand(
                actorId,
                " searchPostProjectionRepair ",
                20,
                " repair stale projection "
        ));

        assertThat(result.jobName()).isEqualTo("searchPostProjectionRepair");
        assertThat(result.accepted()).isTrue();
        assertThat(result.processedCount()).isEqualTo(20);
        assertThat(result.repairedCount()).isEqualTo(18);
        assertThat(result.skippedCount()).isEqualTo(2);
        assertThat(result.result()).isEqualTo(GovernanceResult.ACCEPTED.name());
        verify(triggerPort).trigger(new TriggerCompensationCommand(
                actorId,
                "searchPostProjectionRepair",
                20,
                "repair stale projection"
        ));
    }

    @Test
    void triggerShouldRecordAuditAndMetricsOnAcceptedResult() {
        UUID actorId = uuid(99);
        when(triggerPort.trigger(any())).thenReturn(new CompensationTriggerResult(
                "hotFeedProjectionRepair",
                true,
                10,
                8,
                2,
                GovernanceResult.ACCEPTED.name(),
                "accepted"
        ));

        service.trigger(new TriggerCompensationCommand(actorId, "hotFeedProjectionRepair", 10, "repair hot feed"));

        verify(governanceMetrics).recordCompensationTrigger("hotFeedProjectionRepair", GovernanceResult.ACCEPTED.name());
        verify(governanceMetrics).recordGovernanceAction("COMPENSATION_TRIGGER", GovernanceResult.ACCEPTED.name());
        verify(auditPort).record(any(RecordGovernanceAuditCommand.class));
    }

    @Test
    void triggerShouldRecordFailedMetricAndAuditWhenPortFails() {
        UUID actorId = uuid(99);
        when(triggerPort.trigger(any())).thenThrow(new IllegalStateException("owner action failed"));

        CompensationTriggerResult result = service.trigger(new TriggerCompensationCommand(
                actorId,
                "noticeProjectionRepair",
                10,
                "repair notice projection"
        ));

        assertThat(result.jobName()).isEqualTo("noticeProjectionRepair");
        assertThat(result.accepted()).isFalse();
        assertThat(result.result()).isEqualTo(GovernanceResult.FAILED.name());
        assertThat(result.message()).contains("owner action failed");
        verify(governanceMetrics).recordCompensationTrigger("noticeProjectionRepair", GovernanceResult.FAILED.name());
        verify(governanceMetrics).recordGovernanceAction("COMPENSATION_TRIGGER", GovernanceResult.FAILED.name());
        verify(auditPort).record(any(RecordGovernanceAuditCommand.class));
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }
}
