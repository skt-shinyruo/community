package com.nowcoder.community.user.service;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.user.dao.UserMapper;
import com.nowcoder.community.user.entity.User;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

import static com.nowcoder.community.common.api.CommonErrorCode.NOT_FOUND;
import static com.nowcoder.community.common.api.CommonErrorCode.INVALID_ARGUMENT;

@Service
public class UserService {

    private final UserMapper userMapper;

    public UserService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    public User getById(int userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(NOT_FOUND, "用户不存在");
        }
        return user;
    }

    public User getByUsername(String username) {
        if (!StringUtils.hasText(username)) {
            throw new BusinessException(INVALID_ARGUMENT, "username 不能为空");
        }
        User user = userMapper.selectByName(username);
        if (user == null) {
            throw new BusinessException(NOT_FOUND, "用户不存在");
        }
        return user;
    }

    public void updateHeaderUrl(int userId, String headerUrl) {
        userMapper.updateHeader(userId, headerUrl);
    }

    public List<User> listUserSummariesByIds(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return userMapper.selectUserSummariesByIds(ids);
    }
}
