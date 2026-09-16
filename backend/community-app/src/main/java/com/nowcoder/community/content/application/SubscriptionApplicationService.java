package com.nowcoder.community.content.application;

import com.nowcoder.community.content.domain.repository.SubscriptionRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class SubscriptionApplicationService {

    private final SubscriptionRepository subscriptionRepository;

    public SubscriptionApplicationService(SubscriptionRepository subscriptionRepository) {
        this.subscriptionRepository = subscriptionRepository;
    }

    public List<UUID> listSubscribedCategoryIds(UUID userId) {
        return subscriptionRepository.listSubscribedCategoryIds(userId);
    }
}
