package com.nowcoder.community.user.api.query;

import com.nowcoder.community.user.api.model.RefreshTokenSessionView;

public interface UserRefreshTokenSessionQueryApi {

    RefreshTokenSessionView find(String tokenHash);
}
