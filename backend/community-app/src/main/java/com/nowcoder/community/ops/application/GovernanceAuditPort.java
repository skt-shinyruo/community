package com.nowcoder.community.ops.application;

import com.nowcoder.community.ops.application.command.RecordGovernanceAuditCommand;

public interface GovernanceAuditPort {

    void record(RecordGovernanceAuditCommand command);
}
