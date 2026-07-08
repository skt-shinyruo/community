package com.nowcoder.community.content.api.query;

import com.nowcoder.community.content.api.model.HotFeedCacheStatusView;
import com.nowcoder.community.content.api.model.HotFeedDegradationSignalView;

import java.util.UUID;

public interface HotFeedCacheGovernanceQueryApi {

    HotFeedCacheStatusView getStatus(String scope, UUID boardId);

    HotFeedDegradationSignalView getDegradationSignal();
}
