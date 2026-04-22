package com.nowcoder.community.growth.api.query;

import com.nowcoder.community.growth.api.model.LegacyRewardAccountView;

import java.util.UUID;

public interface LegacyRewardAccountQueryApi {

    LegacyRewardAccountView getLegacyRewardAccount(UUID userId);
}
