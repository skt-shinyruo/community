package com.nowcoder.community.user.infrastructure.api;

import com.nowcoder.community.user.api.model.UserSummaryView;
import com.nowcoder.community.user.api.query.UserLookupQueryApi;
import com.nowcoder.community.user.application.UserReadApplicationService;
import com.nowcoder.community.user.application.result.UserSummaryResult;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class UserReadQueryApiAdapter implements UserLookupQueryApi {

    private final UserReadApplicationService delegate;

    public UserReadQueryApiAdapter(UserReadApplicationService delegate) {
        this.delegate = delegate;
    }

    @Override
    public UserSummaryView getSummaryById(UUID userId) {
        return toSummaryView(delegate.getSummaryById(userId));
    }

    @Override
    public UserSummaryView getSummaryByUsername(String username) {
        return toSummaryView(delegate.getSummaryByUsername(username));
    }

    @Override
    public UserSummaryView findSummaryByEmailOrNull(String email) {
        return toSummaryView(delegate.findSummaryByEmailOrNull(email));
    }

    @Override
    public List<UserSummaryView> listSummariesByIds(List<UUID> userIds) {
        return delegate.listSummariesByIds(userIds).stream()
                .map(this::toSummaryView)
                .toList();
    }

    private UserSummaryView toSummaryView(UserSummaryResult user) {
        if (user == null || user.id() == null) {
            return null;
        }
        return new UserSummaryView(user.id(), user.username(), user.headerUrl(), user.type());
    }
}
