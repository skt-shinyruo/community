package com.nowcoder.community.user.service;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.user.exception.UserErrorCode;
import com.nowcoder.community.user.mapper.UserMapper;
import com.nowcoder.community.user.entity.User;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.atLeastOnce;
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

        assertThatThrownBy(() -> service.register("alice", "pw", "alice2@example.com", Duration.ofMinutes(30)))
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

        assertThatThrownBy(() -> service.register("alice", "pw", "alice@example.com", Duration.ofMinutes(30)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(UserErrorCode.EMAIL_ALREADY_EXISTS);

        verify(userMapper, never()).insertUser(any());
    }

    @Test
    void registerShouldTranslateDuplicateKeyRaceForUsername() {
        InternalUserService service = new InternalUserService(userMapper);
        User absent = null;
        when(userMapper.selectByName("alice")).thenReturn(absent, absent);
        when(userMapper.selectByEmail("alice@example.com")).thenReturn(absent);
        when(userMapper.insertUser(any())).thenThrow(new DuplicateKeyException("uk_user_username"));

        assertThatThrownBy(() -> service.register("alice", "pw", "alice@example.com", Duration.ofMinutes(30)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(UserErrorCode.USER_ALREADY_EXISTS);
    }

    @Test
    void registerShouldTranslateDuplicateKeyRaceForEmail() {
        InternalUserService service = new InternalUserService(userMapper);
        User absent = null;
        when(userMapper.selectByName("alice")).thenReturn(absent, absent);
        when(userMapper.selectByEmail("alice@example.com")).thenReturn(absent, absent);
        when(userMapper.insertUser(any())).thenThrow(new DuplicateKeyException("uk_user_email"));

        assertThatThrownBy(() -> service.register("alice", "pw", "alice@example.com", Duration.ofMinutes(30)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(UserErrorCode.EMAIL_ALREADY_EXISTS);
    }

    @Test
    void registerShouldRecycleExpiredPendingUserBeforeInsert() {
        InternalUserService service = new InternalUserService(userMapper);
        User expired = expiredPendingUser(5, "alice", "alice@example.com");
        when(userMapper.selectByName("alice")).thenReturn(expired, null);
        when(userMapper.selectByEmail("alice@example.com")).thenReturn(expired, null);
        when(userMapper.deletePendingUserIfExpired(anyInt(), anyInt(), any(Date.class))).thenReturn(1, 0);
        when(userMapper.insertUser(any())).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(7);
            return 1;
        });

        User created = service.register("alice", "pw", "alice@example.com", Duration.ofMinutes(30));

        assertThat(created.getId()).isEqualTo(7);
        verify(userMapper, atLeastOnce()).deletePendingUserIfExpired(anyInt(), anyInt(), any(Date.class));
        verify(userMapper).insertUser(any());
    }

    @Test
    void getPendingRegistrationUserShouldDeleteExpiredPendingUserAndThrowNotFound() {
        InternalUserService service = new InternalUserService(userMapper);
        when(userMapper.selectById(7)).thenReturn(expiredPendingUser(7, "alice", "alice@example.com"));

        assertThatThrownBy(() -> service.getPendingRegistrationUser(7, Duration.ofMinutes(30)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(UserErrorCode.USER_NOT_FOUND);

        verify(userMapper).deletePendingUserIfExpired(anyInt(), anyInt(), any(Date.class));
    }

    @Test
    void cleanupExpiredPendingUsersShouldDelegateToMapper() {
        InternalUserService service = new InternalUserService(userMapper);
        when(userMapper.deleteExpiredPendingUsers(anyInt(), any(Date.class))).thenReturn(3);

        assertThat(service.cleanupExpiredPendingUsers(Duration.ofMinutes(30))).isEqualTo(3);
        verify(userMapper).deleteExpiredPendingUsers(anyInt(), any(Date.class));
    }

    private User existingUser(int id, String username, String email) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setEmail(email);
        return user;
    }

    private User expiredPendingUser(int id, String username, String email) {
        User user = existingUser(id, username, email);
        user.setStatus(0);
        user.setCreateTime(Date.from(Instant.now().minus(Duration.ofMinutes(31))));
        return user;
    }
}
