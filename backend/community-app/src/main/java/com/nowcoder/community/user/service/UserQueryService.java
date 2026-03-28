package com.nowcoder.community.user.service;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.user.api.model.UserGrowthProfileView;
import com.nowcoder.community.user.api.model.UserSummaryView;
import com.nowcoder.community.user.api.query.UserLookupQueryApi;
import com.nowcoder.community.user.api.query.UserProfileQueryApi;
import com.nowcoder.community.user.entity.User;
import com.nowcoder.community.user.mapper.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;

import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;
import static com.nowcoder.community.user.exception.UserErrorCode.USER_NOT_FOUND;

@Service
public class UserQueryService implements UserLookupQueryApi, UserProfileQueryApi {

    private final UserMapper userMapper;

    public UserQueryService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    public User getById(int userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(USER_NOT_FOUND);
        }
        return user;
    }

    public User getByUsername(String username) {
        if (!StringUtils.hasText(username)) {
            throw new BusinessException(INVALID_ARGUMENT, "username 不能为空");
        }
        User user = userMapper.selectByName(username);
        if (user == null) {
            throw new BusinessException(USER_NOT_FOUND);
        }
        return user;
    }

    public User findByEmailOrNull(String email) {
        String value = safeTrim(email);
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(INVALID_ARGUMENT, "email 不能为空");
        }
        return userMapper.selectByEmail(value);
    }

    public List<User> listUserSummariesByIds(List<Integer> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        List<Integer> ids = userIds.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .limit(200)
                .toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        List<User> users = userMapper.selectUserSummariesByIds(ids);
        return users == null ? List.of() : users;
    }

    @Override
    public UserSummaryView getSummaryById(int userId) {
        if (userId <= 0) {
            throw new BusinessException(INVALID_ARGUMENT, "userId 非法");
        }
        return toSummaryView(userMapper.selectById(userId));
    }

    @Override
    public UserSummaryView getSummaryByUsername(String username) {
        String value = safeTrim(username);
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(INVALID_ARGUMENT, "username 不能为空");
        }
        return toSummaryView(userMapper.selectByName(value));
    }

    @Override
    public UserSummaryView findSummaryByEmailOrNull(String email) {
        return toSummaryView(findByEmailOrNull(email));
    }

    @Override
    public List<UserSummaryView> listSummariesByIds(List<Integer> userIds) {
        return listUserSummariesByIds(userIds).stream()
                .map(this::toSummaryView)
                .filter(Objects::nonNull)
                .toList();
    }

    @Override
    public UserGrowthProfileView getGrowthProfile(int userId) {
        return toGrowthProfile(getById(userId));
    }

    private UserSummaryView toSummaryView(User user) {
        if (user == null || user.getId() <= 0) {
            return null;
        }
        return new UserSummaryView(user.getId(), user.getUsername(), user.getHeaderUrl(), user.getType());
    }

    private UserGrowthProfileView toGrowthProfile(User user) {
        if (user == null || user.getId() <= 0) {
            return null;
        }
        return new UserGrowthProfileView(
                user.getId(),
                user.getUsername(),
                user.getScore(),
                levelForScore(user.getScore()),
                user.getEmail(),
                user.getStatus(),
                user.getHeaderUrl()
        );
    }

    private int levelForScore(int score) {
        int normalizedScore = Math.max(0, score);
        return (normalizedScore / PointsService.LEVEL_SCORE_STEP) + 1;
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }
}
