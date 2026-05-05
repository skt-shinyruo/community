package com.nowcoder.community.content.domain.repository;

import java.util.List;
import java.util.UUID;

public interface SubscriptionRepository {

    List<UUID> listSubscribedCategoryIds(UUID userId);
}
