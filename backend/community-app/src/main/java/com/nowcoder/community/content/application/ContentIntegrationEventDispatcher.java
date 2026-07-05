package com.nowcoder.community.content.application;

import com.nowcoder.community.content.contracts.event.ContentContractEvent;

public interface ContentIntegrationEventDispatcher {

    void dispatch(String eventKey, ContentContractEvent event);
}
