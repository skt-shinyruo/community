package com.nowcoder.community.user.infrastructure.persistence;

import com.nowcoder.community.user.application.port.UsernameAuthenticationSubjectPort;
import com.nowcoder.community.user.infrastructure.persistence.mapper.UserMapper;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.Objects;

@Repository
public class MyBatisUsernameAuthenticationSubjectAdapter implements UsernameAuthenticationSubjectPort {

    private static final String SUBJECT_PREFIX = "utf8mb4_unicode_ci:v1:";

    private final UserMapper userMapper;

    public MyBatisUsernameAuthenticationSubjectAdapter(UserMapper userMapper) {
        this.userMapper = Objects.requireNonNull(userMapper, "userMapper must not be null");
    }

    @Override
    public String resolve(String validatedUsername) {
        String digest = userMapper.selectUsernameAuthenticationSubjectDigest(validatedUsername);
        if (!StringUtils.hasText(digest)) {
            throw new IllegalStateException("failed to derive username authentication subject");
        }
        return SUBJECT_PREFIX + digest;
    }
}
