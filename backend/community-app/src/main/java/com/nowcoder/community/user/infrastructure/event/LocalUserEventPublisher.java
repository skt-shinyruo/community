package com.nowcoder.community.user.infrastructure.event;

import com.nowcoder.community.user.application.port.UserEventPublisher;
import com.nowcoder.community.user.contracts.event.UserContractEvent;
import com.nowcoder.community.user.contracts.event.UserEventTypes;
import com.nowcoder.community.user.contracts.event.UserPolicyChangedPayload;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class LocalUserEventPublisher implements UserEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    public LocalUserEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Override
    public void publishUserPolicyChanged(UserPolicyChangedPayload payload) {
        applicationEventPublisher.publishEvent(new UserContractEvent(
                UUID.randomUUID().toString(),
                UserEventTypes.USER_POLICY_CHANGED,
                payload
        ));
    }
}
