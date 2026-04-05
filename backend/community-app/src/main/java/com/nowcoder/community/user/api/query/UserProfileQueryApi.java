package com.nowcoder.community.user.api.query;

import com.nowcoder.community.user.api.model.UserProfileView;

public interface UserProfileQueryApi {

    UserProfileView getProfile(int userId);
}
