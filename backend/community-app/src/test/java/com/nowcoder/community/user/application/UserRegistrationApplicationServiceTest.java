package com.nowcoder.community.user.application;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.id.UuidV7Generator;
import com.nowcoder.community.user.application.result.PendingRegistrationUserResult;
import com.nowcoder.community.user.application.result.UserCredentialResult;
import com.nowcoder.community.user.domain.event.UserPolicyEventPublisher;
import com.nowcoder.community.user.domain.model.UserAccount;
import com.nowcoder.community.user.domain.repository.UserRepository;
import com.nowcoder.community.user.domain.service.UserRegistrationDomainService;
import com.nowcoder.community.user.exception.UserErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.nowcoder.community.common.exception.CommonErrorCode.INTERNAL_ERROR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserRegistrationApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-04-28T01:00:00Z");

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserPolicyEventPublisher userPolicyEventPublisher;

    @Test
    void registerPendingUserShouldCreatePendingUserWithEncodedPassword() {
        UserRegistrationApplicationService service = service();
        when(userRepository.findByUsername("alice")).thenReturn(Optional.empty(), Optional.empty());
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.empty(), Optional.empty());

        PendingRegistrationUserResult created = service.registerPendingUser(
                "  alice  ",
                "  secret  ",
                "  alice@example.com  ",
                Duration.ofMinutes(30)
        );

        ArgumentCaptor<UserAccount> userCaptor = ArgumentCaptor.forClass(UserAccount.class);
        verify(userRepository).insertPendingUser(userCaptor.capture());
        UserAccount inserted = userCaptor.getValue();
        assertThat(created.userId()).isEqualTo(inserted.id());
        assertThat(created.username()).isEqualTo("alice");
        assertThat(created.email()).isEqualTo("alice@example.com");
        assertThat(inserted.status()).isEqualTo(0);
        assertThat(inserted.type()).isEqualTo(0);
        assertThat(inserted.salt()).isEmpty();
        assertThat(new BCryptPasswordEncoder().matches("secret", inserted.encodedPassword())).isTrue();
        verify(userPolicyEventPublisher).publishUserPolicyChanged(eq(inserted.id()), eq(true), any(Instant.class));
    }

    @Test
    void registerPendingUserShouldRejectDuplicateUsernameFromPrecheck() {
        UserRegistrationApplicationService service = service();
        when(userRepository.findByUsername("alice"))
                .thenReturn(Optional.of(user(userId(1), "alice", "alice@example.com", 1, NOW)));

        assertThatThrownBy(() -> service.registerPendingUser("alice", "pw", "alice2@example.com", Duration.ofMinutes(30)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(UserErrorCode.USER_ALREADY_EXISTS);

        verify(userRepository, never()).insertPendingUser(any());
    }

    @Test
    void registerPendingUserShouldRejectDuplicateEmailFromPrecheck() {
        UserRegistrationApplicationService service = service();
        when(userRepository.findByUsername("alice")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("alice@example.com"))
                .thenReturn(Optional.of(user(userId(2), "bob", "alice@example.com", 1, NOW)));

        assertThatThrownBy(() -> service.registerPendingUser("alice", "pw", "alice@example.com", Duration.ofMinutes(30)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(UserErrorCode.EMAIL_ALREADY_EXISTS);

        verify(userRepository, never()).insertPendingUser(any());
    }

    @Test
    void registerPendingUserShouldTranslateDuplicateKeyRaceForUsername() {
        UserRegistrationApplicationService service = service();
        when(userRepository.findByUsername("alice")).thenReturn(Optional.empty(), Optional.empty());
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.empty());
        doThrow(new DuplicateKeyException("uk_user_username")).when(userRepository).insertPendingUser(any());

        assertThatThrownBy(() -> service.registerPendingUser("alice", "pw", "alice@example.com", Duration.ofMinutes(30)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(UserErrorCode.USER_ALREADY_EXISTS);
    }

    @Test
    void registerPendingUserShouldRecycleExpiredPendingUserBeforeInsert() {
        UserRegistrationApplicationService service = service();
        UserAccount expired = expiredPendingUser(userId(5), "alice", "alice@example.com");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(expired), Optional.empty());
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(expired), Optional.empty());
        when(userRepository.deletePendingUserIfExpired(eq(expired.id()), eq(0), any(Instant.class))).thenReturn(1, 0);

        PendingRegistrationUserResult created = service.registerPendingUser("alice", "pw", "alice@example.com", Duration.ofMinutes(30));

        assertThat(created.userId()).isNotNull();
        verify(userRepository, atLeastOnce()).deletePendingUserIfExpired(eq(expired.id()), eq(0), any(Instant.class));
        verify(userRepository).insertPendingUser(any());
        verify(userPolicyEventPublisher).publishUserPolicyChanged(eq(expired.id()), eq(false), any(Instant.class));
        verify(userPolicyEventPublisher).publishUserPolicyChanged(eq(created.userId()), eq(true), any(Instant.class));
    }

    @Test
    void getPendingUserShouldDeleteExpiredPendingUserAndThrowNotFound() {
        UserRegistrationApplicationService service = service();
        UUID userId = userId(7);
        when(userRepository.findById(userId)).thenReturn(Optional.of(expiredPendingUser(userId, "alice", "alice@example.com")));
        when(userRepository.deletePendingUserIfExpired(eq(userId), eq(0), any(Instant.class))).thenReturn(1);

        assertThatThrownBy(() -> service.getPendingUser(userId, Duration.ofMinutes(30)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(UserErrorCode.USER_NOT_FOUND);

        verify(userRepository).deletePendingUserIfExpired(eq(userId), eq(0), any(Instant.class));
        verify(userPolicyEventPublisher).publishUserPolicyChanged(eq(userId), eq(false), any(Instant.class));
    }

    @Test
    void activatePendingUserShouldUpdateStatusForExistingPendingUser() {
        UserRegistrationApplicationService service = service();
        UUID userId = userId(7);
        when(userRepository.findById(userId)).thenReturn(Optional.of(expiredPendingUser(userId, "alice", "alice@example.com")));

        UserCredentialResult result = service.activatePendingUser(userId);

        assertThat(result.userId()).isEqualTo(userId);
        assertThat(result.status()).isEqualTo(1);
        verify(userRepository).updateStatus(userId, 1);
        verify(userPolicyEventPublisher).publishUserPolicyChanged(eq(userId), eq(true), any(Instant.class));
    }

    @Test
    void activatePendingUserShouldPropagateStatusUpdateFailure() {
        UserRegistrationApplicationService service = service();
        UUID userId = userId(7);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user(userId, "alice", "alice@example.com", 0, NOW)));
        doThrow(new BusinessException(INTERNAL_ERROR, "更新用户状态失败")).when(userRepository).updateStatus(userId, 1);

        assertThatThrownBy(() -> service.activatePendingUser(userId))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(INTERNAL_ERROR);
    }

    @Test
    void cleanupExpiredPendingUsersShouldDeleteBatchAndPublishDeletionEvents() {
        UserRegistrationApplicationService service = service();
        UUID userId1 = userId(11);
        UUID userId2 = userId(12);
        when(userRepository.listExpiredPendingUserIds(eq(0), any(Instant.class), anyInt())).thenReturn(List.of(userId1, userId2));
        when(userRepository.deletePendingUserIfExpired(any(), eq(0), any(Instant.class))).thenReturn(1);

        assertThat(service.cleanupExpiredPendingUsers(Duration.ofMinutes(30))).isEqualTo(2);

        verify(userRepository).listExpiredPendingUserIds(eq(0), any(Instant.class), eq(500));
        verify(userPolicyEventPublisher).publishUserPolicyChanged(eq(userId1), eq(false), any(Instant.class));
        verify(userPolicyEventPublisher).publishUserPolicyChanged(eq(userId2), eq(false), any(Instant.class));
    }

    @Test
    void deletePendingUserShouldDeleteOnlyPendingUser() {
        UserRegistrationApplicationService service = service();
        UUID userId = userId(8);
        when(userRepository.deletePendingUser(userId, 0)).thenReturn(1);

        service.deletePendingUser(userId);

        verify(userRepository).deletePendingUser(userId, 0);
        verify(userPolicyEventPublisher).publishUserPolicyChanged(eq(userId), eq(false), any(Instant.class));
    }

    private UserRegistrationApplicationService service() {
        return new UserRegistrationApplicationService(
                userRepository,
                new UserRegistrationDomainService(Clock.fixed(NOW, ZoneOffset.UTC)),
                new UuidV7Generator(Clock.fixed(NOW, ZoneOffset.UTC)),
                userPolicyEventPublisher
        );
    }

    private UserAccount expiredPendingUser(UUID id, String username, String email) {
        return user(id, username, email, 0, NOW.minus(Duration.ofMinutes(31)));
    }

    private UserAccount user(UUID id, String username, String email, int status, Instant createdAt) {
        return new UserAccount(
                id,
                username,
                "encoded",
                "",
                email,
                0,
                status,
                "h",
                Date.from(createdAt),
                0,
                null,
                null
        );
    }

    private UUID userId(int tail) {
        return UUID.fromString("018f7f66-3f8f-7a5a-8e32-" + String.format("%012x", tail));
    }
}
