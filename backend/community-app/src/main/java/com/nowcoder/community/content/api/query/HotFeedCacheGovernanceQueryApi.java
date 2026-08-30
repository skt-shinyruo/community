package com.nowcoder.community.content.api.query;

import com.nowcoder.community.content.api.model.HotFeedCacheStatusView;
import com.nowcoder.community.content.api.model.HotFeedDegradationSignal;

import java.util.UUID;

public interface HotFeedCacheGovernanceQueryApi {

    HotFeedCacheStatusView getStatus(String scope, UUID boardId);

    HotFeedDegradationSignal getDegradationSignal();
}
