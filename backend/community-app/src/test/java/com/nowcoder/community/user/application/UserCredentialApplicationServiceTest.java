package com.nowcoder.community.user.application;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.user.application.port.UsernameAuthenticationSubjectPort;
import com.nowcoder.community.user.api.model.UserAuthenticationResultView;
import com.nowcoder.community.user.api.model.UserCredentialView;
import com.nowcoder.community.user.domain.model.UserAccount;
import com.nowcoder.community.user.domain.repository.UserRepository;
import com.nowcoder.community.user.domain.service.PasswordPolicyDomainService;
import com.nowcoder.community.user.domain.service.UserCredentialDomainService;
import com.nowcoder.community.user.domain.service.UsernamePolicyDomainService;
import com.nowcoder.community.user.exception.UserErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Instant;
import java.time.Clock;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;
import static com.nowcoder.community.common.exception.CommonErrorCode.SERVICE_UNAVAILABLE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserCredentialApplicationServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UsernameAuthenticationSubjectPort usernameAuthenticationSubjectPort;

    @Test
    void authenticationSubjectShouldBeDerivedWithoutLookingUpAccountExistence() {
        UserCredentialApplicationService service = service();
        when(usernameAuthenticationSubjectPort.resolve("Alice"))
                .thenReturn("utf8mb4_unicode_ci:v1:abc123");

        String subject = service.authenticationSubject(" Alice ");

        assertThat(subject).isEqualTo("utf8mb4_unicode_ci:v1:abc123");
        verify(usernameAuthenticationSubjectPort).resolve("Alice");
        verifyNoInteractions(userRepository);
    }

    @Test
    void authenticationSubjectShouldFailClosedWhenTheCollationResolverFails() {
        UserCredentialApplicationService service = service();
        when(usernameAuthenticationSubjectPort.resolve("alice"))
                .thenThrow(new RuntimeException("database unavailable"));

        assertThatThrownBy(() -> service.authenticationSubject("alice"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(SERVICE_UNAVAILABLE))
                .hasRootCauseMessage("database unavailable");

        verifyNoInteractions(userRepository);
    }

    @Test
    void authenticateShouldRejectBlankCredentials() {
        UserCredentialApplicationService service = service();

        UserAuthenticationResultView result = service.authenticate("  ", "secret");

        assertThat(result.failure()).isEqualTo(UserAuthenticationResultView.Failure.INVALID_CREDENTIALS);
        assertThat(result.user()).isNull();
        verifyNoInteractions(userRepository);
    }

    @Test
    void authenticateShouldRejectDisabledUserAfterPasswordMatches() {
        UserCredentialApplicationService service = service();
        when(userRepository.findByUsername("alice"))
                .thenReturn(Optional.of(disabledUser(uuid(7), "alice", "pw")));

        UserAuthenticationResultView result = service.authenticate("alice", "pw");

        assertThat(result.failure()).isEqualTo(UserAuthenticationResultView.Failure.USER_DISABLED);
        assertThat(result.user()).isNotNull();
        assertThat(result.user().username()).isEqualTo("alice");
    }

    @Test
    void authenticateShouldNotRevealDisabledUserWhenPasswordIsWrong() {
        UserCredentialApplicationService service = service();
        when(userRepository.findByUsername("alice"))
                .thenReturn(Optional.of(disabledUser(uuid(7), "alice", "correct-password")));

        UserAuthenticationResultView result = service.authenticate("alice", "wrong-password");

        assertThat(result.failure()).isEqualTo(UserAuthenticationResultView.Failure.INVALID_CREDENTIALS);
        assertThat(result.user()).isNull();
    }

    @Test
    void authenticateShouldUseGenericInvalidCredentialsForMissingUser() {
        UserCredentialApplicationService service = service();
        when(userRepository.findByUsername("missing")).thenReturn(Optional.empty());

        UserAuthenticationResultView result = service.authenticate("missing", "wrong-password");

        assertThat(result.failure()).isEqualTo(UserAuthenticationResultView.Failure.INVALID_CREDENTIALS);
        assertThat(result.user()).isNull();
    }

    @Test
    void authenticateShouldRejectActivelyBannedUser() {
        UserCredentialApplicationService service = service();
        UserAccount user = new UserAccount(
                uuid(7),
                "alice",
                new BCryptPasswordEncoder().encode("secret12"),
                "",
                "alice@example.com",
                0,
                1,
                "h7",
                Date.from(Instant.now()),
                null,
                Instant.now().plusSeconds(600),
                0L,
                99L
        );
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        UserAuthenticationResultView result = service.authenticate("alice", "secret12");

        assertThat(result.failure()).isEqualTo(UserAuthenticationResultView.Failure.USER_DISABLED);
        assertThat(result.user().securityVersion()).isEqualTo(99L);
        assertThat(result.user().loginAllowed()).isFalse();
        assertThat(result.user().refreshAllowed()).isFalse();
    }

    @Test
    void authenticateShouldRejectNonBcryptPasswordHash() {
        UserCredentialApplicationService service = service();
        UUID userId = uuid(7);
        UserAccount user = activeUser(userId, "alice", "plain-hash", "abc");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        UserAuthenticationResultView authenticationResult = service.authenticate("alice", "secret");

        assertThat(authenticationResult.failure()).isEqualTo(UserAuthenticationResultView.Failure.INVALID_CREDENTIALS);
        assertThat(authenticationResult.user()).isNull();
        verify(userRepository, never()).updatePassword(any(), any(), anyLong());
    }

    @Test
    void authenticateShouldUseDummyHashForMalformedBcryptPrefix() {
        UserCredentialApplicationService service = service();
        UserAccount user = activeUser(uuid(7), "alice", "$2a$10$malformed", "");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        UserAuthenticationResultView result = service.authenticate("alice", "secret12");

        assertThat(result.failure()).isEqualTo(UserAuthenticationResultView.Failure.INVALID_CREDENTIALS);
        assertThat(result.user()).isNull();
    }

    @Test
    void malformedStoredHashMustNeverBecomeAuthenticatableThroughDummyHash() {
        UserCredentialApplicationService service = service();
        UserAccount user = activeUser(uuid(8), "alice", "not-a-bcrypt-hash", "");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        UserCredentialApplicationService.PreparedAuthentication preparation =
                service.prepareAuthentication("alice");

        assertThat(preparation.storedHashUsable()).isFalse();
        assertThat(service.authenticate(preparation, "any-password").failure())
                .isEqualTo(UserAuthenticationResultView.Failure.INVALID_CREDENTIALS);
    }

    @Test
    void unsafeUsernameMustNotReachUserRepository() {
        UserCredentialApplicationService service = service();

        UserCredentialApplicationService.PreparedAuthentication preparation =
                service.prepareAuthentication("alice\u200D");

        assertThat(preparation.user()).isNull();
        assertThat(preparation.storedHashUsable()).isFalse();
        verifyNoInteractions(userRepository);
    }

    @Test
    void safeAliasMustNotAuthenticateAnUnsafeStoredUsername() {
        UserCredentialApplicationService service = service();
        UserAccount unsafeStoredUser = activeUser(
                uuid(7),
                "a\u200Dlice",
                new BCryptPasswordEncoder().encode("secret12"),
                ""
        );
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(unsafeStoredUser));

        UserCredentialApplicationService.PreparedAuthentication preparation =
                service.prepareAuthentication("alice");

        assertThat(preparation.user()).isNull();
        assertThat(preparation.storedHashUsable()).isFalse();
        assertThat(service.authenticate(preparation, "secret12").failure())
                .isEqualTo(UserAuthenticationResultView.Failure.INVALID_CREDENTIALS);
    }

    @Test
    void authenticateShouldNotTrimPasswordBeforeMatching() {
        UserCredentialApplicationService service = service();
        UUID userId = uuid(7);
        String encoded = new BCryptPasswordEncoder().encode("secret12");
        UserAccount user = activeUser(userId, "alice", encoded, "");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        UserAuthenticationResultView authenticationResult = service.authenticate("alice", " secret12 ");

        assertThat(authenticationResult.failure()).isEqualTo(UserAuthenticationResultView.Failure.INVALID_CREDENTIALS);
        assertThat(authenticationResult.user()).isNull();
    }

    @Test
    void getByUserIdShouldProjectCredentialResult() {
        UserCredentialApplicationService service = service();
        UUID userId = uuid(7);
        when(userRepository.findById(userId)).thenReturn(Optional.of(activeUser(userId, "alice", "encoded", "")));

        UserCredentialView credential = service.getByUserId(userId);

        assertThat(credential).extracting(
                UserCredentialView::userId,
                UserCredentialView::username,
                UserCredentialView::status,
                UserCredentialView::type,
                UserCredentialView::headerUrl,
                UserCredentialView::securityVersion
        ).containsExactly(userId, "alice", 1, 0, "h7", 0L);
    }

    @Test
    void findByEmailShouldUseCanonicalLowercaseAddress() {
        UserCredentialApplicationService service = service();
        UserAccount user = activeUser(uuid(7), "alice", "encoded", "");
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));

        UserCredentialView credential = service.findByEmailOrNull("  Alice@Example.COM  ");

        assertThat(credential.email()).isEqualTo("alice@example.com");
        verify(userRepository).findByEmail("alice@example.com");
    }

    @Test
    void getByUserIdShouldRejectMissingUser() {
        UserCredentialApplicationService service = service();
        UUID userId = uuid(7);
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getByUserId(userId))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(UserErrorCode.USER_NOT_FOUND);
    }

    @Test
    void updatePasswordShouldRejectMissingUser() {
        UserCredentialApplicationService service = service();
        UUID userId = uuid(7);
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updatePassword(userId, "secret12"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(UserErrorCode.USER_NOT_FOUND);
    }

    @Test
    void updatePasswordShouldPersistBcryptHashForExistingUser() {
        UserCredentialApplicationService service = service();
        UUID userId = uuid(7);
        when(userRepository.findById(userId)).thenReturn(Optional.of(activeUser(userId, "alice", "encoded", "")));
        when(userRepository.nextUserSecurityVersion(userId)).thenReturn(123L);

        service.updatePassword(userId, "secret12");

        ArgumentCaptor<String> passwordCaptor = ArgumentCaptor.forClass(String.class);
        verify(userRepository).updatePassword(eq(userId), passwordCaptor.capture(), eq(123L));
        assertThat(new BCryptPasswordEncoder().matches("secret12", passwordCaptor.getValue())).isTrue();
    }

    @Test
    void updatePasswordIfSecurityVersionShouldHashAndUseExpectedVersionCas() {
        UserCredentialApplicationService service = service();
        UUID userId = uuid(8);
        when(userRepository.nextUserSecurityVersion(userId)).thenReturn(124L);
        when(userRepository.updatePasswordIfSecurityVersion(
                eq(userId),
                any(String.class),
                eq(124L),
                eq(17L)
        )).thenReturn(true);

        assertThat(service.updatePasswordIfSecurityVersion(userId, "secret12", 17L)).isTrue();

        ArgumentCaptor<String> passwordCaptor = ArgumentCaptor.forClass(String.class);
        verify(userRepository).updatePasswordIfSecurityVersion(
                eq(userId),
                passwordCaptor.capture(),
                eq(124L),
                eq(17L)
        );
        assertThat(new BCryptPasswordEncoder().matches("secret12", passwordCaptor.getValue())).isTrue();
    }

    @Test
    void updatePasswordIfSecurityVersionShouldReportStaleCasWithoutUnconditionalWrite() {
        UserCredentialApplicationService service = service();
        UUID userId = uuid(9);
        when(userRepository.nextUserSecurityVersion(userId)).thenReturn(125L);
        when(userRepository.updatePasswordIfSecurityVersion(
                eq(userId),
                any(String.class),
                eq(125L),
                eq(17L)
        )).thenReturn(false);

        assertThat(service.updatePasswordIfSecurityVersion(userId, "secret12", 17L)).isFalse();

        verify(userRepository, never()).updatePassword(any(), any(), anyLong());
    }

    @Test
    void authoritiesOfShouldMapUserTypesToExpectedRoles() {
        UserCredentialApplicationService service = service();
        UserCredentialView admin = new UserCredentialView(uuid(1), "admin", 1, 1, "h1", 0L, true, true);
        UserCredentialView moderator = new UserCredentialView(uuid(2), "mod", 1, 2, "h2", 0L, true, true);
        UserCredentialView regular = new UserCredentialView(uuid(3), "user", 1, 0, "h3", 0L, true, true);

        assertThat(service.authoritiesOf(null)).isEmpty();
        assertThat(service.authoritiesOf(admin)).isEqualTo(List.of("ROLE_ADMIN"));
        assertThat(service.authoritiesOf(moderator)).isEqualTo(List.of("ROLE_MODERATOR"));
        assertThat(service.authoritiesOf(regular)).isEqualTo(List.of("ROLE_USER"));
    }

    @Test
    void updatePasswordShouldRejectBlankPassword() {
        UserCredentialApplicationService service = service();

        assertThatThrownBy(() -> service.updatePassword(uuid(7), "  "))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(INVALID_ARGUMENT);
    }

    @Test
    void updatePasswordShouldRejectLeadingOrTrailingWhitespaceInsteadOfTrimming() {
        UserCredentialApplicationService service = service();

        assertThatThrownBy(() -> service.updatePassword(uuid(7), " secret12 "))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(INVALID_ARGUMENT);

        verifyNoInteractions(userRepository);
    }

    @Test
    void updatePasswordShouldRejectWeakPassword() {
        UserCredentialApplicationService service = service();

        assertThatThrownBy(() -> service.updatePassword(uuid(7), "aaaaaaaa"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(INVALID_ARGUMENT);

        verifyNoInteractions(userRepository);
    }

    @Test
    void validatePasswordPolicyShouldRejectWeakPasswordWithoutRepositoryAccess() {
        UserCredentialApplicationService service = service();

        assertThatThrownBy(() -> service.validatePasswordPolicy("aaaaaaaa"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(INVALID_ARGUMENT);

        verifyNoInteractions(userRepository);
    }

    private UserCredentialApplicationService service() {
        return new UserCredentialApplicationService(
                userRepository,
                new UserCredentialDomainService(new UsernamePolicyDomainService()),
                new PasswordPolicyDomainService(),
                usernameAuthenticationSubjectPort,
                Clock.systemUTC()
        );
    }

    private UserAccount activeUser(UUID id, String username, String password, String salt) {
        return new UserAccount(id, username, password, salt, username + "@example.com", 0, 1, "h7", Date.from(Instant.now()), null, null, 0L, 0L);
    }

    private UserAccount disabledUser(UUID id, String username, String rawPassword) {
        UserAccount user = activeUser(id, username, new BCryptPasswordEncoder().encode(rawPassword), "");
        return new UserAccount(user.id(), user.username(), user.encodedPassword(), user.salt(), user.email(), user.type(), 0, user.headerUrl(), user.createTime(), user.muteUntil(), user.banUntil(), user.policyVersion(), 0L);
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }
}
