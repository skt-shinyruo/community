package com.nowcoder.community.content.application.port;

import java.util.List;
import java.util.UUID;

public interface SubscriptionContentPort {

    void subscribeCategory(UUID userId, UUID categoryId);

    void unsubscribeCategory(UUID userId, UUID categoryId);

    boolean hasSubscribedCategory(UUID userId, UUID categoryId);

    List<UUID> listSubscribedCategoryIds(UUID userId);
}
