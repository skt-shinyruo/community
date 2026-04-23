package com.nowcoder.community.user.service;

import com.nowcoder.community.user.api.model.UserProfileView;
import com.nowcoder.community.user.api.model.UserSummaryView;
import com.nowcoder.community.user.api.query.UserLookupQueryApi;
import com.nowcoder.community.user.api.query.UserProfileQueryApi;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class UserReadApplicationService implements UserLookupQueryApi, UserProfileQueryApi {

    private final UserQueryService userQueryService;

    public UserReadApplicationService(UserQueryService userQueryService) {
        this.userQueryService = userQueryService;
    }

    @Override
    public UserSummaryView getSummaryById(UUID userId) {
        return userQueryService.getSummaryById(userId);
    }

    @Override
    public UserSummaryView getSummaryByUsername(String username) {
        return userQueryService.getSummaryByUsername(username);
    }

    @Override
    public UserSummaryView findSummaryByEmailOrNull(String email) {
        return userQueryService.findSummaryByEmailOrNull(email);
    }

    @Override
    public List<UserSummaryView> listSummariesByIds(List<UUID> userIds) {
        return userQueryService.listSummariesByIds(userIds);
    }

    @Override
    public UserProfileView getProfile(UUID userId) {
        return userQueryService.getProfile(userId);
    }
}
