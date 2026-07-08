package com.nowcoder.community.ops.application;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.exception.CommonErrorCode;
import com.nowcoder.community.ops.application.command.RecordGovernanceAuditCommand;
import com.nowcoder.community.ops.application.command.TriggerCompensationCommand;
import com.nowcoder.community.ops.application.result.CompensationTriggerResult;
import com.nowcoder.community.ops.domain.model.GovernanceAction;
import com.nowcoder.community.ops.domain.model.GovernanceResult;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Set;

@Service
public class CompensationGovernanceApplicationService {

    private static final Set<String> ALLOWED_JOBS = Set.of(
            "outboxRecoverExpiredLeases",
            "searchPostProjectionRepair",
            "hotFeedProjectionRepair",
            "growthTaskProjectionRepair",
            "noticeProjectionRepair"
    );

    private final CompensationTriggerPort compensationTriggerPort;
    private final GovernanceMetrics governanceMetrics;
    private final GovernanceAuditPort governanceAuditPort;

    public CompensationGovernanceApplicationService(
            CompensationTriggerPort compensationTriggerPort,
            GovernanceMetrics governanceMetrics,
            GovernanceAuditPort governanceAuditPort
    ) {
        this.compensationTriggerPort = Objects.requireNonNull(compensationTriggerPort, "compensationTriggerPort must not be null");
        this.governanceMetrics = Objects.requireNonNull(governanceMetrics, "governanceMetrics must not be null");
        this.governanceAuditPort = Objects.requireNonNull(governanceAuditPort, "governanceAuditPort must not be null");
    }

    public CompensationTriggerResult trigger(TriggerCompensationCommand command) {
        TriggerCompensationCommand c = validate(command);
        CompensationTriggerResult result;
        try {
            result = compensationTriggerPort.trigger(c);
            if (result == null) {
                result = new CompensationTriggerResult(
                        c.jobName(),
                        false,
                        0,
                        0,
                        0,
                        GovernanceResult.SKIPPED.name(),
                        "compensation job returned no result"
                );
            }
        } catch (RuntimeException ex) {
            result = new CompensationTriggerResult(
                    c.jobName(),
                    false,
                    0,
                    0,
                    0,
                    GovernanceResult.FAILED.name(),
                    ex.getMessage()
            );
        }
        record(c, result);
        return result;
    }

    private TriggerCompensationCommand validate(TriggerCompensationCommand command) {
        if (command == null || command.actorUserId() == null) {
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT, "actorUserId is required");
        }
        TriggerCompensationCommand c = command.normalized();
        if (!ALLOWED_JOBS.contains(c.jobName())) {
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT, "compensation job is not allow-listed");
        }
        if (c.limit() < 1 || c.limit() > 500) {
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT, "limit must be between 1 and 500");
        }
        if (c.reason().isBlank()) {
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT, "compensation reason is required");
        }
        return c;
    }

    private void record(TriggerCompensationCommand command, CompensationTriggerResult result) {
        String resultValue = result.result() == null || result.result().isBlank()
                ? GovernanceResult.FAILED.name()
                : result.result();
        governanceMetrics.recordCompensationTrigger(command.jobName(), resultValue);
        governanceMetrics.recordGovernanceAction(GovernanceAction.COMPENSATION_TRIGGER.name(), resultValue);
        governanceAuditPort.record(new RecordGovernanceAuditCommand(
                GovernanceAction.COMPENSATION_TRIGGER.name(),
                command.actorUserId(),
                "compensation_job",
                command.jobName(),
                "job=" + command.jobName(),
                command.reason(),
                "{\"limit\":" + command.limit() + "}",
                resultValue,
                "{\"accepted\":" + result.accepted()
                        + ",\"processed\":" + result.processedCount()
                        + ",\"repaired\":" + result.repairedCount()
                        + ",\"skipped\":" + result.skippedCount()
                        + ",\"message\":\"" + safeJson(result.message()) + "\"}",
                null
        ));
    }

    private static String safeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
