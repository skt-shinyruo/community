package com.nowcoder.community.im.application;

import com.nowcoder.community.im.common.event.UserBlockRelationChanged;
import com.nowcoder.community.im.common.event.UserMessagingPolicyChanged;

public interface ImPolicyIntegrationEventDispatcher {

    void dispatchUserMessagingPolicyChanged(String eventKey, UserMessagingPolicyChanged event);

    void dispatchUserBlockRelationChanged(String eventKey, UserBlockRelationChanged event);
}
