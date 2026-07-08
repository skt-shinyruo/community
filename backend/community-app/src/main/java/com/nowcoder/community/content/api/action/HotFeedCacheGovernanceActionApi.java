package com.nowcoder.community.content.api.action;

import com.nowcoder.community.content.api.model.HotFeedCachePrewarmRequest;
import com.nowcoder.community.content.api.model.HotFeedCachePrewarmResultView;
import com.nowcoder.community.content.api.model.HotFeedDegradationSignalView;
import com.nowcoder.community.content.api.model.UpdateHotFeedDegradationSignalRequest;

public interface HotFeedCacheGovernanceActionApi {

    HotFeedCachePrewarmResultView prewarm(HotFeedCachePrewarmRequest request);

    HotFeedDegradationSignalView updateDegradationSignal(UpdateHotFeedDegradationSignalRequest request);
}
