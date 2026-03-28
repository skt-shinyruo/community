package com.nowcoder.community.user.service;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.user.mapper.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;

@Service
public class UserService {

    private final UserMapper userMapper;

    public UserService(UserMapper userMapper) {
        this.userMapper = userMapper;
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
}
