package com.nowcoder.community.user.service;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.user.exception.UserErrorCode;
import com.nowcoder.community.user.mapper.UserMapper;
import com.nowcoder.community.user.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InternalUserServiceTest {

    @Mock
    private UserMapper userMapper;

    @Test
    void registerShouldRejectDuplicateUsernameFromPrecheck() {
        InternalUserService service = new InternalUserService(userMapper);
        when(userMapper.selectByName("alice")).thenReturn(existingUser(1, "alice", "alice@example.com"));

        assertThatThrownBy(() -> service.register("alice", "pw", "alice2@example.com"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(UserErrorCode.USER_ALREADY_EXISTS);

        verify(userMapper, never()).insertUser(any());
    }

    @Test
    void registerShouldRejectDuplicateEmailFromPrecheck() {
        InternalUserService service = new InternalUserService(userMapper);
        when(userMapper.selectByName("alice")).thenReturn(null);
        when(userMapper.selectByEmail("alice@example.com")).thenReturn(existingUser(2, "bob", "alice@example.com"));

        assertThatThrownBy(() -> service.register("alice", "pw", "alice@example.com"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(UserErrorCode.EMAIL_ALREADY_EXISTS);

        verify(userMapper, never()).insertUser(any());
    }

    @Test
    void registerShouldTranslateDuplicateKeyRaceForUsername() {
        InternalUserService service = new InternalUserService(userMapper);
        when(userMapper.selectByName("alice")).thenReturn((User) null, existingUser(3, "alice", "alice@example.com"));
        when(userMapper.selectByEmail("alice@example.com")).thenReturn(null);
        when(userMapper.insertUser(any())).thenThrow(new DuplicateKeyException("uk_user_username"));

        assertThatThrownBy(() -> service.register("alice", "pw", "alice@example.com"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(UserErrorCode.USER_ALREADY_EXISTS);
    }

    @Test
    void registerShouldTranslateDuplicateKeyRaceForEmail() {
        InternalUserService service = new InternalUserService(userMapper);
        when(userMapper.selectByName("alice")).thenReturn((User) null, (User) null);
        when(userMapper.selectByEmail("alice@example.com")).thenReturn((User) null, existingUser(4, "bob", "alice@example.com"));
        when(userMapper.insertUser(any())).thenThrow(new DuplicateKeyException("uk_user_email"));

        assertThatThrownBy(() -> service.register("alice", "pw", "alice@example.com"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(UserErrorCode.EMAIL_ALREADY_EXISTS);
    }

    private User existingUser(int id, String username, String email) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setEmail(email);
        return user;
    }
}
