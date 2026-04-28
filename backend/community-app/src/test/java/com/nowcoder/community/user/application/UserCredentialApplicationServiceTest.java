package com.nowcoder.community.user.application;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.user.application.result.UserAuthenticationResult;
import com.nowcoder.community.user.application.result.UserCredentialResult;
import com.nowcoder.community.user.domain.model.UserAccount;
import com.nowcoder.community.user.domain.repository.UserRepository;
import com.nowcoder.community.user.domain.service.UserCredentialDomainService;
import com.nowcoder.community.user.exception.UserErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserCredentialApplicationServiceTest {

    @Mock
    private UserRepository userRepository;

    @Test
    void authenticateShouldRejectBlankCredentials() {
        UserCredentialApplicationService service = service();

        UserAuthenticationResult result = service.authenticate("  ", "secret");

        assertThat(result.failure()).isEqualTo(UserAuthenticationResult.Failure.INVALID_CREDENTIALS);
        assertThat(result.user()).isNull();
        verifyNoInteractions(userRepository);
    }

    @Test
    void authenticateShouldRejectDisabledUser() {
        UserCredentialApplicationService service = service();
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(disabledUser(uuid(7), "alice")));

        UserAuthenticationResult result = service.authenticate("alice", "pw");

        assertThat(result.failure()).isEqualTo(UserAuthenticationResult.Failure.USER_DISABLED);
        assertThat(result.user()).isNotNull();
        assertThat(result.user().username()).isEqualTo("alice");
    }

    @Test
    void authenticateShouldUpgradeLegacyPasswordHashOnSuccessfulMatch() {
        UserCredentialApplicationService service = service();
        UUID userId = uuid(7);
        UserAccount user = activeUser(userId, "alice", md5("secretabc"), "abc");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        UserAuthenticationResult authenticationResult = service.authenticate("alice", "secret");
        UserCredentialResult authenticated = authenticationResult.user();

        ArgumentCaptor<String> passwordCaptor = ArgumentCaptor.forClass(String.class);
        verify(userRepository).updatePassword(eq(userId), passwordCaptor.capture());
        assertThat(authenticationResult.authenticated()).isTrue();
        assertThat(authenticated).extracting(
                UserCredentialResult::userId,
                UserCredentialResult::username,
                UserCredentialResult::status,
                UserCredentialResult::type
        ).containsExactly(userId, "alice", 1, 0);
        assertThat(new BCryptPasswordEncoder().matches("secret", passwordCaptor.getValue())).isTrue();
    }

    @Test
    void getByUserIdShouldProjectCredentialResult() {
        UserCredentialApplicationService service = service();
        UUID userId = uuid(7);
        when(userRepository.findById(userId)).thenReturn(Optional.of(activeUser(userId, "alice", "encoded", "")));

        UserCredentialResult credential = service.getByUserId(userId);

        assertThat(credential).extracting(
                UserCredentialResult::userId,
                UserCredentialResult::username,
                UserCredentialResult::status,
                UserCredentialResult::type,
                UserCredentialResult::headerUrl
        ).containsExactly(userId, "alice", 1, 0, "h7");
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

        assertThatThrownBy(() -> service.updatePassword(userId, "secret"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(UserErrorCode.USER_NOT_FOUND);
    }

    @Test
    void updatePasswordShouldPersistBcryptHashForExistingUser() {
        UserCredentialApplicationService service = service();
        UUID userId = uuid(7);
        when(userRepository.findById(userId)).thenReturn(Optional.of(activeUser(userId, "alice", "encoded", "")));

        service.updatePassword(userId, "  secret  ");

        ArgumentCaptor<String> passwordCaptor = ArgumentCaptor.forClass(String.class);
        verify(userRepository).updatePassword(eq(userId), passwordCaptor.capture());
        assertThat(new BCryptPasswordEncoder().matches("secret", passwordCaptor.getValue())).isTrue();
    }

    @Test
    void authoritiesOfShouldMapUserTypesToExpectedRoles() {
        UserCredentialApplicationService service = service();
        UserCredentialResult admin = new UserCredentialResult(uuid(1), "admin", 1, 1, "h1");
        UserCredentialResult moderator = new UserCredentialResult(uuid(2), "mod", 1, 2, "h2");
        UserCredentialResult regular = new UserCredentialResult(uuid(3), "user", 1, 0, "h3");

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
                .satisfies(ex -> {
                    BusinessException businessException = (BusinessException) ex;
                    assertThat(businessException.getErrorCode()).isEqualTo(INVALID_ARGUMENT);
                    assertThat(businessException.getMessage()).isEqualTo("newPassword 不能为空");
                });
    }

    private UserCredentialApplicationService service() {
        return new UserCredentialApplicationService(userRepository, new UserCredentialDomainService());
    }

    private UserAccount activeUser(UUID id, String username, String password, String salt) {
        return new UserAccount(
                id,
                username,
                password,
                salt,
                username + "@example.com",
                0,
                1,
                "h7",
                Date.from(Instant.now()),
                0,
                null,
                null
        );
    }

    private UserAccount disabledUser(UUID id, String username) {
        UserAccount user = activeUser(id, username, "encoded", "");
        return new UserAccount(
                user.id(),
                user.username(),
                user.encodedPassword(),
                user.salt(),
                user.email(),
                user.type(),
                0,
                user.headerUrl(),
                user.createTime(),
                user.score(),
                user.muteUntil(),
                user.banUntil()
        );
    }

    private String md5(String input) {
        return DigestUtils.md5DigestAsHex(input.getBytes(StandardCharsets.UTF_8));
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }
}
