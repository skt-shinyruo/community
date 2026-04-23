package com.nowcoder.community.user.service;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.user.api.model.UserProfileView;
import com.nowcoder.community.user.api.model.UserSummaryView;
import com.nowcoder.community.user.entity.User;
import com.nowcoder.community.user.mapper.UserMapper;
import com.nowcoder.community.wallet.api.query.WalletAccountQueryApi;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;
import static com.nowcoder.community.user.exception.UserErrorCode.USER_NOT_FOUND;

@Service
public class UserQueryService {

    private static final int LEVEL_SCORE_STEP = 100;

    private final UserMapper userMapper;
    private final WalletAccountQueryApi walletAccountQueryApi;

    public UserQueryService(UserMapper userMapper, WalletAccountQueryApi walletAccountQueryApi) {
        this.userMapper = userMapper;
        this.walletAccountQueryApi = walletAccountQueryApi;
    }

    public User getById(UUID userId) {
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

    public List<User> listUserSummariesByIds(List<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = userIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .limit(200)
                .toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        List<User> users = userMapper.selectUserSummariesByIds(ids);
        return users == null ? List.of() : users;
    }

    public UserSummaryView getSummaryById(UUID userId) {
        if (userId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "userId 非法");
        }
        return toSummaryView(userMapper.selectById(userId));
    }

    public UserSummaryView getSummaryByUsername(String username) {
        String value = safeTrim(username);
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(INVALID_ARGUMENT, "username 不能为空");
        }
        return toSummaryView(userMapper.selectByName(value));
    }

    public UserSummaryView findSummaryByEmailOrNull(String email) {
        return toSummaryView(findByEmailOrNull(email));
    }

    public List<UserSummaryView> listSummariesByIds(List<UUID> userIds) {
        return listUserSummariesByIds(userIds).stream()
                .map(this::toSummaryView)
                .filter(Objects::nonNull)
                .toList();
    }

    public UserProfileView getProfile(UUID userId) {
        return toProfileView(getById(userId));
    }

    private UserSummaryView toSummaryView(User user) {
        if (user == null || user.getId() == null) {
            return null;
        }
        return new UserSummaryView(user.getId(), user.getUsername(), user.getHeaderUrl(), user.getType());
    }

    private UserProfileView toProfileView(User user) {
        if (user == null || user.getId() == null) {
            return null;
        }
        return new UserProfileView(
                user.getId(),
                user.getUsername(),
                user.getHeaderUrl(),
                user.getType(),
                user.getStatus(),
                user.getCreateTime(),
                user.getScore(),
                levelForScore(user.getScore()),
                walletAccountQueryApi.balanceOfUser(user.getId()),
                walletAccountQueryApi.statusOfUser(user.getId())
        );
    }

    private int levelForScore(int score) {
        int normalizedScore = Math.max(0, score);
        return (normalizedScore / LEVEL_SCORE_STEP) + 1;
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }
}
