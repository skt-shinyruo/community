package com.nowcoder.community.growth.api.query;

import com.nowcoder.community.growth.api.model.UserLevelSummaryView;

import java.util.UUID;

public interface UserLevelQueryApi {

    UserLevelSummaryView evaluateLevel(UUID userId);
}
