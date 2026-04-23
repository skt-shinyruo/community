package com.nowcoder.community.user.service;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.user.contracts.event.UserPolicyChangedPayload;
import com.nowcoder.community.user.entity.User;
import com.nowcoder.community.user.event.UserEventPublisher;
import com.nowcoder.community.user.exception.UserErrorCode;
import com.nowcoder.community.user.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static com.nowcoder.community.common.exception.CommonErrorCode.INTERNAL_ERROR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserRegistrationServiceTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private UserEventPublisher userEventPublisher;

    @Test
    void registerShouldCreatePendingUserWithEncodedPassword() {
        UserRegistrationService service = new UserRegistrationService(userMapper, userEventPublisher);
        UUID generatedId = UUID.fromString("018f7f66-3f8f-7a5a-8e32-1a2b3c4d5e6f");
        when(userMapper.selectByName("alice")).thenReturn((User) null).thenReturn((User) null);
        when(userMapper.selectByEmail("alice@example.com")).thenReturn((User) null).thenReturn((User) null);
        when(userMapper.insertUser(any())).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(generatedId);
            return 1;
        });

        User created = service.register("  alice  ", "  secret  ", "  alice@example.com  ", Duration.ofMinutes(30));

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insertUser(userCaptor.capture());
        assertThat(created.getId()).isEqualTo(generatedId);
        assertThat(userCaptor.getValue().getUsername()).isEqualTo("alice");
        assertThat(userCaptor.getValue().getEmail()).isEqualTo("alice@example.com");
        assertThat(userCaptor.getValue().getStatus()).isEqualTo(0);
        assertThat(userCaptor.getValue().getType()).isEqualTo(0);
        assertThat(userCaptor.getValue().getSalt()).isEmpty();
        assertThat(new BCryptPasswordEncoder().matches("secret", userCaptor.getValue().getPassword())).isTrue();
        ArgumentCaptor<UserPolicyChangedPayload> payloadCaptor = ArgumentCaptor.forClass(UserPolicyChangedPayload.class);
        verify(userEventPublisher).publishUserPolicyChanged(payloadCaptor.capture());
        assertThat(payloadCaptor.getValue().getUserId()).isEqualTo(generatedId);
    }

    @Test
    void registerShouldRejectDuplicateUsernameFromPrecheck() {
        UserRegistrationService service = new UserRegistrationService(userMapper, userEventPublisher);
        when(userMapper.selectByName("alice")).thenReturn(existingUser(userId(1), "alice", "alice@example.com"));

        assertThatThrownBy(() -> service.register("alice", "pw", "alice2@example.com", Duration.ofMinutes(30)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(UserErrorCode.USER_ALREADY_EXISTS);

        verify(userMapper, never()).insertUser(any());
    }

    @Test
    void registerShouldRejectDuplicateEmailFromPrecheck() {
        UserRegistrationService service = new UserRegistrationService(userMapper, userEventPublisher);
        when(userMapper.selectByName("alice")).thenReturn(null);
        when(userMapper.selectByEmail("alice@example.com")).thenReturn(existingUser(userId(2), "bob", "alice@example.com"));

        assertThatThrownBy(() -> service.register("alice", "pw", "alice@example.com", Duration.ofMinutes(30)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(UserErrorCode.EMAIL_ALREADY_EXISTS);

        verify(userMapper, never()).insertUser(any());
    }

    @Test
    void registerShouldTranslateDuplicateKeyRaceForUsername() {
        UserRegistrationService service = new UserRegistrationService(userMapper, userEventPublisher);
        when(userMapper.selectByName("alice")).thenReturn((User) null).thenReturn((User) null);
        when(userMapper.selectByEmail("alice@example.com")).thenReturn((User) null);
        when(userMapper.insertUser(any())).thenThrow(new DuplicateKeyException("uk_user_username"));

        assertThatThrownBy(() -> service.register("alice", "pw", "alice@example.com", Duration.ofMinutes(30)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(UserErrorCode.USER_ALREADY_EXISTS);
    }

    @Test
    void registerShouldTranslateDuplicateKeyRaceForEmail() {
        UserRegistrationService service = new UserRegistrationService(userMapper, userEventPublisher);
        when(userMapper.selectByName("alice")).thenReturn((User) null).thenReturn((User) null);
        when(userMapper.selectByEmail("alice@example.com")).thenReturn((User) null).thenReturn((User) null);
        when(userMapper.insertUser(any())).thenThrow(new DuplicateKeyException("uk_user_email"));

        assertThatThrownBy(() -> service.register("alice", "pw", "alice@example.com", Duration.ofMinutes(30)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(UserErrorCode.EMAIL_ALREADY_EXISTS);
    }

    @Test
    void registerShouldRecycleExpiredPendingUserBeforeInsert() {
        UserRegistrationService service = new UserRegistrationService(userMapper, userEventPublisher);
        User expired = expiredPendingUser(userId(5), "alice", "alice@example.com");
        UUID createdId = userId(7);
        when(userMapper.selectByName("alice")).thenReturn(expired).thenReturn((User) null);
        when(userMapper.selectByEmail("alice@example.com")).thenReturn(expired).thenReturn((User) null);
        when(userMapper.deletePendingUserIfExpired(any(), anyInt(), any(Date.class))).thenReturn(1, 0);
        when(userMapper.insertUser(any())).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(createdId);
            return 1;
        });

        User created = service.register("alice", "pw", "alice@example.com", Duration.ofMinutes(30));

        assertThat(created.getId()).isEqualTo(createdId);
        verify(userMapper, atLeastOnce()).deletePendingUserIfExpired(any(), anyInt(), any(Date.class));
        verify(userMapper).insertUser(any());
        ArgumentCaptor<UserPolicyChangedPayload> payloadCaptor = ArgumentCaptor.forClass(UserPolicyChangedPayload.class);
        verify(userEventPublisher, atLeastOnce()).publishUserPolicyChanged(payloadCaptor.capture());
        assertThat(payloadCaptor.getAllValues())
                .extracting(UserPolicyChangedPayload::getUserId)
                .contains(createdId, expired.getId());
    }

    @Test
    void getPendingRegistrationUserShouldDeleteExpiredPendingUserAndThrowNotFound() {
        UserRegistrationService service = new UserRegistrationService(userMapper, userEventPublisher);
        UUID userId = userId(7);
        when(userMapper.selectById(userId)).thenReturn(expiredPendingUser(userId, "alice", "alice@example.com"));
        when(userMapper.deletePendingUserIfExpired(any(), anyInt(), any(Date.class))).thenReturn(1);

        assertThatThrownBy(() -> service.getPendingRegistrationUser(userId, Duration.ofMinutes(30)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(UserErrorCode.USER_NOT_FOUND);

        verify(userMapper).deletePendingUserIfExpired(any(), anyInt(), any(Date.class));
        ArgumentCaptor<UserPolicyChangedPayload> payloadCaptor = ArgumentCaptor.forClass(UserPolicyChangedPayload.class);
        verify(userEventPublisher).publishUserPolicyChanged(payloadCaptor.capture());
        assertThat(payloadCaptor.getValue().getUserId()).isEqualTo(userId);
    }

    @Test
    void activateUserShouldUpdateStatusForExistingPendingUser() {
        UserRegistrationService service = new UserRegistrationService(userMapper, userEventPublisher);
        UUID userId = userId(7);
        when(userMapper.selectById(userId)).thenReturn(expiredPendingUser(userId, "alice", "alice@example.com"));
        when(userMapper.updateStatus(userId, 1)).thenReturn(1);

        service.activateUser(userId);

        verify(userMapper).updateStatus(userId, 1);
        ArgumentCaptor<UserPolicyChangedPayload> payloadCaptor = ArgumentCaptor.forClass(UserPolicyChangedPayload.class);
        verify(userEventPublisher).publishUserPolicyChanged(payloadCaptor.capture());
        assertThat(payloadCaptor.getValue().getUserId()).isEqualTo(userId);
    }

    @Test
    void activateUserShouldThrowWhenStatusUpdateFails() {
        UserRegistrationService service = new UserRegistrationService(userMapper, userEventPublisher);
        UUID userId = userId(7);
        when(userMapper.selectById(userId)).thenReturn(existingUser(userId, "alice", "alice@example.com"));
        when(userMapper.updateStatus(userId, 1)).thenReturn(0);

        assertThatThrownBy(() -> service.activateUser(userId))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(INTERNAL_ERROR);
    }

    @Test
    void cleanupExpiredPendingUsersShouldDelegateToMapper() {
        UserRegistrationService service = new UserRegistrationService(userMapper, userEventPublisher);
        UUID userId1 = userId(11);
        UUID userId2 = userId(12);
        when(userMapper.selectExpiredPendingUserIds(anyInt(), any(Date.class))).thenReturn(List.of(userId1, userId2));
        when(userMapper.deletePendingUserIfExpired(any(), anyInt(), any(Date.class))).thenReturn(1);

        assertThat(service.cleanupExpiredPendingUsers(Duration.ofMinutes(30))).isEqualTo(2);
        verify(userMapper).selectExpiredPendingUserIds(anyInt(), any(Date.class));
        verify(userMapper, atLeastOnce()).deletePendingUserIfExpired(any(), anyInt(), any(Date.class));
        ArgumentCaptor<UserPolicyChangedPayload> payloadCaptor = ArgumentCaptor.forClass(UserPolicyChangedPayload.class);
        verify(userEventPublisher, atLeastOnce()).publishUserPolicyChanged(payloadCaptor.capture());
        assertThat(payloadCaptor.getAllValues())
                .extracting(UserPolicyChangedPayload::getUserId)
                .containsExactly(userId1, userId2);
    }

    @Test
    void deletePendingUserShouldDeleteOnlyPendingUser() {
        UserRegistrationService service = new UserRegistrationService(userMapper, userEventPublisher);
        UUID userId = userId(8);
        when(userMapper.deletePendingUser(userId, 0)).thenReturn(1);

        service.deletePendingUser(userId);

        verify(userMapper).deletePendingUser(userId, 0);
        ArgumentCaptor<UserPolicyChangedPayload> payloadCaptor = ArgumentCaptor.forClass(UserPolicyChangedPayload.class);
        verify(userEventPublisher).publishUserPolicyChanged(payloadCaptor.capture());
        assertThat(payloadCaptor.getValue().getUserId()).isEqualTo(userId);
    }

    private User existingUser(UUID id, String username, String email) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setEmail(email);
        return user;
    }

    private User expiredPendingUser(UUID id, String username, String email) {
        User user = existingUser(id, username, email);
        user.setStatus(0);
        user.setCreateTime(Date.from(Instant.now().minus(Duration.ofMinutes(31))));
        return user;
    }

    private UUID userId(int tail) {
        return UUID.fromString("018f7f66-3f8f-7a5a-8e32-" + String.format("%012x", tail));
    }
}
