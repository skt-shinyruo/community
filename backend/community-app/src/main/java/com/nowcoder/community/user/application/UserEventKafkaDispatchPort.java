package com.nowcoder.community.user.application;

import com.nowcoder.community.user.contracts.event.UserContractEvent;

public interface UserEventKafkaDispatchPort {

    void send(String topic, String key, UserContractEvent event);
}
