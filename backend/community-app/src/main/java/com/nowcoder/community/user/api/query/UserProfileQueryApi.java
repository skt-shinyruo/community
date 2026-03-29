package com.nowcoder.community.user.api.query;

import com.nowcoder.community.user.api.model.UserGrowthProfileView;
import com.nowcoder.community.user.api.model.UserProfileView;

public interface UserProfileQueryApi {

    UserProfileView getProfile(int userId);

    UserGrowthProfileView getGrowthProfile(int userId);
}
