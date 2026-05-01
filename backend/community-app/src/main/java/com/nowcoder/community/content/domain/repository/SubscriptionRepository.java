package com.nowcoder.community.content.domain.repository;

import java.util.List;
import java.util.UUID;

public interface SubscriptionRepository {

    void subscribeCategory(UUID userId, UUID categoryId);

    void unsubscribeCategory(UUID userId, UUID categoryId);

    boolean hasSubscribedCategory(UUID userId, UUID categoryId);

    List<UUID> listSubscribedCategoryIds(UUID userId);
}
