package com.nowcoder.community.user.service;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.user.mapper.UserMapper;
import com.nowcoder.community.user.entity.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;
import static com.nowcoder.community.user.exception.UserErrorCode.USER_NOT_FOUND;

@Service
public class UserService {

    private final UserMapper userMapper;

    public UserService(UserMapper userMapper) {
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

    @Transactional
    public void updateHeaderUrl(int userId, String headerUrl) {
        int uid = Math.max(0, userId);
        if (uid <= 0) {
            throw new BusinessException(INVALID_ARGUMENT, "userId 非法");
        }
        String url = StringUtils.hasText(headerUrl) ? headerUrl.trim() : "";
        if (!StringUtils.hasText(url)) {
            throw new BusinessException(INVALID_ARGUMENT, "headerUrl 不能为空");
        }
        int updated = userMapper.updateHeader(uid, url);
        if (updated <= 0) {
            throw new BusinessException(com.nowcoder.community.common.exception.CommonErrorCode.INTERNAL_ERROR, "更新头像失败");
        }
    }

    public List<User> listUserSummariesByIds(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return userMapper.selectUserSummariesByIds(ids);
    }
}
