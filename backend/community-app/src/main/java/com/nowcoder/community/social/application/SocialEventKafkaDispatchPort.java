package com.nowcoder.community.social.application;

import com.nowcoder.community.social.contracts.event.SocialContractEvent;

public interface SocialEventKafkaDispatchPort {

    void send(String topic, String key, SocialContractEvent event);
}
