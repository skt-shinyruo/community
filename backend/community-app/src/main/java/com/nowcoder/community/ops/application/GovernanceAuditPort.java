package com.nowcoder.community.ops.application;

import com.nowcoder.community.ops.application.command.RecordGovernanceAuditCommand;
import com.nowcoder.community.ops.application.result.GovernanceAuditResult;

public interface GovernanceAuditPort {

    GovernanceAuditResult record(RecordGovernanceAuditCommand command);
}
