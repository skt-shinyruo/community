package com.nowcoder.community.im.governance.action;

import java.util.UUID;

public interface PrivateMessageGovernanceActionApi {

    void validateCanSendPrivateMessage(UUID fromUserId, UUID toUserId);
}
