package com.nowcoder.community.user.application;

import com.nowcoder.community.user.contracts.event.UserContractEvent;

public interface UserIntegrationEventDispatcher {

    void dispatch(String eventKey, UserContractEvent event);
}
