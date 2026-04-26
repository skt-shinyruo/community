package com.nowcoder.community.analytics.repo;

import java.util.UUID;

public interface AnalyticsUserOrdinalRepository {

    int resolveOrdinal(UUID userId);
}
