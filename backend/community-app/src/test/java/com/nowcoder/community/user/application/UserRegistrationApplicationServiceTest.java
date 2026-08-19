package com.nowcoder.community.user.application;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.id.UuidV7Generator;
import com.nowcoder.community.user.api.action.UserRegistrationActionApi;
import com.nowcoder.community.user.api.model.PreparedRegistrationUserView;
import com.nowcoder.community.user.api.model.UserCredentialView;
import com.nowcoder.community.user.api.model.VerifiedRegistrationUserCommand;
import com.nowcoder.community.user.domain.event.UserPolicyEventPublisher;
import com.nowcoder.community.user.domain.model.UserAccount;
import com.nowcoder.community.user.domain.model.UserRole;
import com.nowcoder.community.user.domain.repository.UserRepository;
import com.nowcoder.community.user.domain.repository.UserRepository.InsertResult;
import com.nowcoder.community.user.domain.service.PasswordPolicyDomainService;
import com.nowcoder.community.user.domain.service.UserRegistrationDomainService;
import com.nowcoder.community.user.domain.service.UsernamePolicyDomainService;
import com.nowcoder.community.user.exception.UserErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
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
    void prepareRegistrationUserShouldReturnPreparedMaterialWithoutWritingUserOrPublishingEvent() {
        UserRegistrationApplicationService service = service();

        PreparedRegistrationUserView prepared = service.prepareRegistrationUser(
                "  alice  ",
                "secret12",
                "  Alice@Example.COM  "
        );

        assertThat(prepared.userId()).isNotNull();
        assertThat(prepared.username()).isEqualTo("alice");
        assertThat(prepared.email()).isEqualTo("alice@example.com");
        assertThat(new BCryptPasswordEncoder().matches("secret12", prepared.encodedPassword())).isTrue();
        assertThat(prepared.headerUrl()).startsWith("http://images.nowcoder.com/head/");
        verify(userRepository).findByEmail("alice@example.com");
        verify(userRepository, never()).insertUser(any());
        verify(userPolicyEventPublisher, never()).publishUserPolicyChanged(any(UUID.class), anyBoolean(), any(Instant.class), anyLong());
    }

    @Test
    void createVerifiedRegistrationUserShouldInsertActiveUserAndPublishExistenceEvent() {
        UserRegistrationApplicationService service = service();
        UUID userId = userId(21);
        String encodedPassword = new BCryptPasswordEncoder().encode("secret12");
        when(userRepository.insertUser(any())).thenReturn(InsertResult.CREATED);
        when(userRepository.nextUserPolicyVersion(userId)).thenReturn(17L);
        when(userRepository.nextUserSecurityVersion(userId)).thenReturn(41L);

        UserRegistrationActionApi.VerifiedRegistrationResult result = service.createVerifiedRegistrationUser(new VerifiedRegistrationUserCommand(
                userId,
                "alice",
                "Alice@Example.COM",
                encodedPassword,
                "http://images.nowcoder.com/head/1t.png"
        ));

        ArgumentCaptor<UserAccount> userCaptor = ArgumentCaptor.forClass(UserAccount.class);
        InOrder writeOrder = inOrder(userRepository);
        writeOrder.verify(userRepository).lockRoleManagement();
        writeOrder.verify(userRepository).insertUser(userCaptor.capture());
        UserAccount inserted = userCaptor.getValue();
        assertThat(inserted.id()).isEqualTo(userId);
        assertThat(inserted.username()).isEqualTo("alice");
        assertThat(inserted.email()).isEqualTo("alice@example.com");
        assertThat(inserted.encodedPassword()).isEqualTo(encodedPassword);
        assertThat(inserted.salt()).isEmpty();
        assertThat(inserted.status()).isEqualTo(1);
        assertThat(inserted.type()).isEqualTo(UserRole.USER.type());
        assertThat(inserted.headerUrl()).isEqualTo("http://images.nowcoder.com/head/1t.png");
        assertThat(inserted.createTime()).isEqualTo(Date.from(NOW));
        assertThat(inserted.muteUntil()).isNull();
        assertThat(inserted.banUntil()).isNull();
        assertThat(inserted.policyVersion()).isZero();
        assertThat(inserted.securityVersion()).isZero();
        assertThat(result.created()).isTrue();
        assertThat(result.user().userId()).isEqualTo(userId);
        assertThat(result.user().status()).isEqualTo(1);
        assertThat(result.user().type()).isEqualTo(UserRole.USER.type());
        assertThat(result.user().headerUrl()).isEqualTo("http://images.nowcoder.com/head/1t.png");
        assertThat(result.user().securityVersion()).isEqualTo(41L);
        assertThat(result.user().loginAllowed()).isTrue();
        assertThat(result.user().refreshAllowed()).isTrue();
        verify(userRepository).nextUserPolicyVersion(userId);
        verify(userRepository).nextUserSecurityVersion(userId);
        verify(userRepository).updateModerationUntil(
                eq(userId),
                eq(null),
                eq(null),
                eq(17L),
                eq(41L),
                eq(0L)
        );
        verify(userPolicyEventPublisher).publishUserPolicyChanged(eq(userId), eq(true), any(Instant.class), eq(17L));
    }

    @Test
    void createVerifiedRegistrationUserShouldRejectNullCommand() {
        UserRegistrationApplicationService service = service();

        assertThatThrownBy(() -> service.createVerifiedRegistrationUser(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("command must not be null");
    }

    @Test
    void createVerifiedRegistrationUserShouldTranslateDuplicateEmailRace() {
        UserRegistrationApplicationService service = service();
        UUID userId = userId(22);
        when(userRepository.insertUser(any())).thenReturn(InsertResult.ALREADY_EXISTS);
        when(userRepository.findByEmail("alice@example.com"))
                .thenReturn(Optional.of(existingUser("other", "alice@example.com")));
        String encodedPassword = new BCryptPasswordEncoder().encode("secret12");

        assertThatThrownBy(() -> service.createVerifiedRegistrationUser(new VerifiedRegistrationUserCommand(
                userId,
                "alice",
                "alice@example.com",
                encodedPassword,
                "h"
        )))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(UserErrorCode.EMAIL_ALREADY_EXISTS);

        verify(userPolicyEventPublisher, never()).publishUserPolicyChanged(any(UUID.class), anyBoolean(), any(Instant.class), anyLong());
    }

    @Test
    void createVerifiedRegistrationUserShouldRejectMalformedEncodedPassword() {
        UserRegistrationApplicationService service = service();

        assertThatThrownBy(() -> service.createVerifiedRegistrationUser(new VerifiedRegistrationUserCommand(
                userId(23),
                "alice",
                "alice@example.com",
                "secret12",
                "h"
        )))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(INVALID_ARGUMENT);

        verify(userRepository, never()).insertUser(any());
        verify(userPolicyEventPublisher, never()).publishUserPolicyChanged(any(UUID.class), anyBoolean(), any(Instant.class), anyLong());
    }

    @Test
    void prepareRegistrationUserShouldRejectWeakPassword() {
        UserRegistrationApplicationService service = service();

        assertThatThrownBy(() -> service.prepareRegistrationUser("alice", "aaaaaaaa", "alice@example.com"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(INVALID_ARGUMENT);

        verify(userRepository, never()).insertUser(any());
    }

    @Test
    void prepareRegistrationUserShouldRejectExistingUsernameBeforeIssuingDraft() {
        UserRegistrationApplicationService service = service();
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(existingUser("alice", "other@example.com")));

        assertThatThrownBy(() -> service.prepareRegistrationUser(" alice ", "secret12", "alice@example.com"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(UserErrorCode.USER_ALREADY_EXISTS);

        verify(userRepository, never()).insertUser(any());
        verify(userPolicyEventPublisher, never()).publishUserPolicyChanged(any(UUID.class), anyBoolean(), any(Instant.class), anyLong());
    }

    @Test
    void prepareRegistrationUserShouldRejectExistingEmailBeforeIssuingDraft() {
        UserRegistrationApplicationService service = service();
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(existingUser("other", "alice@example.com")));

        assertThatThrownBy(() -> service.prepareRegistrationUser("alice", "secret12", " alice@example.com "))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(UserErrorCode.EMAIL_ALREADY_EXISTS);

        verify(userRepository, never()).insertUser(any());
        verify(userPolicyEventPublisher, never()).publishUserPolicyChanged(any(UUID.class), anyBoolean(), any(Instant.class), anyLong());
    }

    private UserRegistrationApplicationService service() {
        return new UserRegistrationApplicationService(
                userRepository,
                new UserRegistrationDomainService(
                        Clock.fixed(NOW, ZoneOffset.UTC),
                        new PasswordPolicyDomainService(),
                        new UsernamePolicyDomainService()
                ),
                new UuidV7Generator(Clock.fixed(NOW, ZoneOffset.UTC)),
                userPolicyEventPublisher,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private UUID userId(int tail) {
        return UUID.fromString("018f7f66-3f8f-7a5a-8e32-" + String.format("%012x", tail));
    }

    private UserAccount existingUser(String username, String email) {
        return new UserAccount(userId(99), username, new BCryptPasswordEncoder().encode("secret12"), "", email, 0, 1, "h", Date.from(NOW), null, null, 0L, 0L);
    }
}
