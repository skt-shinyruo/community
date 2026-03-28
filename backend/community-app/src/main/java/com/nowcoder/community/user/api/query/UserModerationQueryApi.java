package com.nowcoder.community.user.api.query;

import com.nowcoder.community.user.api.model.UserModerationStateView;

public interface UserModerationQueryApi {

    UserModerationStateView getModerationState(int userId);
}
