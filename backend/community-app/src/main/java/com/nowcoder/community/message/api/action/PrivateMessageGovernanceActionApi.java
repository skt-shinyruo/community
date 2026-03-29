package com.nowcoder.community.message.api.action;

public interface PrivateMessageGovernanceActionApi {

    void validateCanSendPrivateMessage(int fromUserId, int toUserId);
}
