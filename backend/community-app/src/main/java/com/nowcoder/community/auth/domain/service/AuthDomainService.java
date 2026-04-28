package com.nowcoder.community.auth.domain.service;

import com.nowcoder.community.auth.exception.AuthErrorCode;
import com.nowcoder.community.common.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AuthDomainService {

    public void requireCredentials(String username, String password) {
        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            throw new BusinessException(AuthErrorCode.INVALID_CREDENTIALS);
        }
    }
}
