package com.nowcoder.community.social.application;

import com.nowcoder.community.social.contracts.event.SocialContractEvent;

public interface SocialIntegrationEventDispatcher {

    void dispatch(String eventKey, SocialContractEvent event);
}
