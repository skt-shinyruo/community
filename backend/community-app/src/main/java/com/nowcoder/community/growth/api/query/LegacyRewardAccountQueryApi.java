package com.nowcoder.community.growth.api.query;

import com.nowcoder.community.growth.api.model.LegacyRewardAccountView;

public interface LegacyRewardAccountQueryApi {

    LegacyRewardAccountView getLegacyRewardAccount(int userId);
}
