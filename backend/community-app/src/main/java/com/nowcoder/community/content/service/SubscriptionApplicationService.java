package com.nowcoder.community.content.service;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class SubscriptionApplicationService {

    private final SubscriptionService subscriptionService;

    public SubscriptionApplicationService(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    public void subscribeCategory(UUID userId, UUID categoryId) {
        subscriptionService.subscribeCategory(userId, categoryId);
    }

    public void unsubscribeCategory(UUID userId, UUID categoryId) {
        subscriptionService.unsubscribeCategory(userId, categoryId);
    }

    public List<UUID> listSubscribedCategoryIds(UUID userId) {
        return subscriptionService.listSubscribedCategoryIds(userId);
    }
}
