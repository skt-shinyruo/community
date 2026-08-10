package com.nowcoder.community.content.application;

import java.util.List;
import java.util.UUID;

public interface SubscriptionQuery {

    List<UUID> listSubscribedCategoryIds(UUID userId);
}
