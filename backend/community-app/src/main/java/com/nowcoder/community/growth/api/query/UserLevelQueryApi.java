package com.nowcoder.community.growth.api.query;

import com.nowcoder.community.growth.api.model.UserLevelSummaryView;

public interface UserLevelQueryApi {

    UserLevelSummaryView evaluateLevel(int userId);
}
