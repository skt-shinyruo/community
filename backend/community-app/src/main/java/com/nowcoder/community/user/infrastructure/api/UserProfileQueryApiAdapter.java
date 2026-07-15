package com.nowcoder.community.user.infrastructure.api;

import com.nowcoder.community.user.api.model.UserProfileView;
import com.nowcoder.community.user.api.query.UserProfileQueryApi;
import com.nowcoder.community.user.application.UserReadApplicationService;
import com.nowcoder.community.user.application.result.UserProfileResult;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class UserProfileQueryApiAdapter implements UserProfileQueryApi {

    private final UserReadApplicationService delegate;

    public UserProfileQueryApiAdapter(UserReadApplicationService delegate) {
        this.delegate = delegate;
    }

    @Override
    public UserProfileView getProfile(UUID userId) {
        UserProfileResult user = delegate.getProfile(userId);
        return user == null ? null : new UserProfileView(
                user.userId(),
                user.username(),
                user.headerUrl(),
                user.type(),
                user.status(),
                user.createTime()
        );
    }
}
