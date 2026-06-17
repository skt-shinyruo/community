package com.nowcoder.community.content.application;

import com.nowcoder.community.content.contracts.event.ContentContractEvent;

public interface ContentEventKafkaDispatchPort {

    void send(String topic, String key, ContentContractEvent event);
}
