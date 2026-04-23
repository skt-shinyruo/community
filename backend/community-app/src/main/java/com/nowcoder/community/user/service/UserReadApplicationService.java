package com.nowcoder.community.user.service;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.user.api.model.UserProfileView;
import com.nowcoder.community.user.api.model.UserSummaryView;
import com.nowcoder.community.user.api.query.UserLookupQueryApi;
import com.nowcoder.community.user.api.query.UserProfileQueryApi;
import com.nowcoder.community.user.dto.UserResolveResponse;
import com.nowcoder.community.user.dto.UserSummaryResponse;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.nowcoder.community.user.exception.UserErrorCode.USER_NOT_FOUND;

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

    public void requireExistingUser(UUID userId) {
        userQueryService.getById(userId);
    }

    public UserResolveResponse resolveByUsername(String username) {
        UserSummaryView user = userQueryService.getSummaryByUsername(username);
        if (user == null) {
            throw new BusinessException(USER_NOT_FOUND);
        }
        UserResolveResponse response = new UserResolveResponse();
        response.setId(user.id());
        response.setUsername(user.username());
        response.setHeaderUrl(user.headerUrl());
        return response;
    }

    public List<UserSummaryResponse> listSummaryResponsesByIds(List<UUID> rawUserIds) {
        if (rawUserIds == null || rawUserIds.isEmpty()) {
            return List.of();
        }

        LinkedHashSet<UUID> dedup = new LinkedHashSet<>();
        for (UUID id : rawUserIds) {
            if (id == null) {
                continue;
            }
            dedup.add(id);
            if (dedup.size() >= 200) {
                break;
            }
        }
        if (dedup.isEmpty()) {
            return List.of();
        }

        List<UUID> ids = new ArrayList<>(dedup);
        List<UserSummaryView> users = userQueryService.listSummariesByIds(ids);
        Map<UUID, UserSummaryResponse> mapped = new HashMap<>();
        for (UserSummaryView user : users) {
            if (user == null || user.id() == null) {
                continue;
            }
            UserSummaryResponse response = new UserSummaryResponse();
            response.setId(user.id());
            response.setUsername(user.username());
            response.setHeaderUrl(user.headerUrl());
            response.setType(user.type());
            mapped.put(user.id(), response);
        }

        List<UserSummaryResponse> ordered = new ArrayList<>();
        for (UUID id : ids) {
            UserSummaryResponse response = mapped.get(id);
            if (response != null) {
                ordered.add(response);
            }
        }
        return ordered;
    }
}
