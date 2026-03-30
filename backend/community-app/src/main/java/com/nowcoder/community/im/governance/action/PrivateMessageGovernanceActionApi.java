package com.nowcoder.community.im.governance.action;

public interface PrivateMessageGovernanceActionApi {

    void validateCanSendPrivateMessage(int fromUserId, int toUserId);
}
