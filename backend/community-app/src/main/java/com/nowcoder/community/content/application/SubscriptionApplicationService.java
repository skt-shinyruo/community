package com.nowcoder.community.content.application;

import com.nowcoder.community.content.application.port.SubscriptionContentPort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class SubscriptionApplicationService {

    private final SubscriptionContentPort subscriptionContentPort;

    public SubscriptionApplicationService(SubscriptionContentPort subscriptionContentPort) {
        this.subscriptionContentPort = subscriptionContentPort;
    }

    public void subscribeCategory(UUID userId, UUID categoryId) {
        subscriptionContentPort.subscribeCategory(userId, categoryId);
    }

    public void unsubscribeCategory(UUID userId, UUID categoryId) {
        subscriptionContentPort.unsubscribeCategory(userId, categoryId);
    }

    public List<UUID> listSubscribedCategoryIds(UUID userId) {
        return subscriptionContentPort.listSubscribedCategoryIds(userId);
    }
}
