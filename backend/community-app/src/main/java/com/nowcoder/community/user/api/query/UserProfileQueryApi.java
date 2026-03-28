package com.nowcoder.community.user.api.query;

import com.nowcoder.community.user.api.model.UserGrowthProfileView;

public interface UserProfileQueryApi {

    UserGrowthProfileView getGrowthProfile(int userId);
}
