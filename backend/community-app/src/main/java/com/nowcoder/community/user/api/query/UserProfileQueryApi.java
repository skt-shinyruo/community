package com.nowcoder.community.user.api.query;

import com.nowcoder.community.user.api.model.UserProfileView;

import java.util.UUID;

public interface UserProfileQueryApi {

    UserProfileView getProfile(UUID userId);
}
