package com.nowcoder.community.user.application;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.id.UuidV7Generator;
import com.nowcoder.community.user.application.command.CreateVerifiedRegistrationUserCommand;
import com.nowcoder.community.user.application.result.UserCredentialResult;
import com.nowcoder.community.user.domain.event.UserPolicyEventPublisher;
import com.nowcoder.community.user.domain.model.UserAccount;
import com.nowcoder.community.user.domain.repository.UserRepository;
import com.nowcoder.community.user.domain.service.PasswordPolicyDomainService;
import com.nowcoder.community.user.domain.service.UserRegistrationDomainService;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserRegistrationInsertOutcomeApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-04-28T01:00:00Z");
    private static final String ENCODED_PASSWORD =
            "$2a$10$7EqJtq98hPqEX7fNZaFWoOHiE9VYh4Vh7H1w52x1x7YjQwlhbR1XK";

    @Test
    void insertContractShouldExposeUserOwnedSemanticOutcomes() throws NoSuchMethodException {
        Method insertUser = UserRepository.class.getMethod("insertUser", UserAccount.class);

        assertThat(insertUser.getReturnType().isEnum())
                .as("UserRepository.insertUser must return a User-owned semantic outcome")
                .isTrue();
        assertThat(Arrays.stream(insertUser.getReturnType().getEnumConstants())
                .map(Object::toString))
                .containsExactlyInAnyOrder("CREATED", "ALREADY_EXISTS", "CONFLICT");
    }

    @Test
    void alreadyExistingInsertShouldReloadAndReturnTheCanonicalReplay() {
        UUID userId = userId(31);
        UserRepository repository = repositoryReturningInsertOutcome("ALREADY_EXISTS");
        UserPolicyEventPublisher eventPublisher = mock(UserPolicyEventPublisher.class);
        UserAccount canonical = account(
                userId,
                "alice",
                "alice@example.com",
                "canonical-header",
                2,
                1,
                17L,
                41L
        );
        when(repository.findById(userId)).thenReturn(Optional.of(canonical));

        UserCredentialResult result = service(repository, eventPublisher)
                .createVerifiedRegistrationUser(command(
                        userId,
                        "alice",
                        "alice@example.com",
                        "canonical-header"
                ));

        assertThat(result).isEqualTo(new UserCredentialResult(
                userId,
                "alice",
                1,
                2,
                "canonical-header",
                41L,
                true,
                true
        ));
        verify(repository).findById(userId);
        verify(repository, never()).nextUserPolicyVersion(any(UUID.class));
        verify(repository, never()).updateModerationUntil(
                any(UUID.class),
                any(),
                any(),
                anyLong(),
                anyLong()
        );
        verify(eventPublisher, never()).publishUserPolicyChanged(
                any(UUID.class),
                anyBoolean(),
                any(Instant.class),
                anyLong()
        );
    }

    @Test
    void alreadyExistingInsertShouldRejectAReplayWithDifferentRegistrationFacts() {
        UUID userId = userId(32);
        UserRepository repository = repositoryReturningInsertOutcome("ALREADY_EXISTS");
        UserPolicyEventPublisher eventPublisher = mock(UserPolicyEventPublisher.class);
        when(repository.findById(userId)).thenReturn(Optional.of(account(
                userId,
                "alice",
                "other@example.com",
                "canonical-header",
                0,
                1,
                17L,
                41L
        )));

        assertThatThrownBy(() -> service(repository, eventPublisher)
                .createVerifiedRegistrationUser(command(
                        userId,
                        "alice",
                        "alice@example.com",
                        "canonical-header"
                )))
                .isInstanceOf(BusinessException.class);

        verify(repository).findById(userId);
        verify(repository, never()).nextUserPolicyVersion(any(UUID.class));
        verify(eventPublisher, never()).publishUserPolicyChanged(
                any(UUID.class),
                anyBoolean(),
                any(Instant.class),
                anyLong()
        );
    }

    @Test
    void unknownIntegrityConflictShouldFailClosedWithoutPublishingSuccess() {
        UUID userId = userId(33);
        UserRepository repository = repositoryReturningInsertOutcome("CONFLICT");
        UserPolicyEventPublisher eventPublisher = mock(UserPolicyEventPublisher.class);

        assertThatThrownBy(() -> service(repository, eventPublisher)
                .createVerifiedRegistrationUser(command(
                        userId,
                        "alice",
                        "alice@example.com",
                        "canonical-header"
                )))
                .isInstanceOf(BusinessException.class);

        verify(repository, never()).findById(any(UUID.class));
        verify(repository, never()).nextUserPolicyVersion(any(UUID.class));
        verify(repository, never()).updateModerationUntil(
                any(UUID.class),
                any(),
                any(),
                anyLong(),
                anyLong()
        );
        verify(eventPublisher, never()).publishUserPolicyChanged(
                any(UUID.class),
                anyBoolean(),
                any(Instant.class),
                anyLong()
        );
    }

    private UserRepository repositoryReturningInsertOutcome(String outcomeName) {
        return mock(UserRepository.class, invocation -> {
            if (!invocation.getMethod().getName().equals("insertUser")) {
                return Answers.RETURNS_DEFAULTS.answer(invocation);
            }
            Class<?> returnType = invocation.getMethod().getReturnType();
            if (!returnType.isEnum()) {
                return Answers.RETURNS_DEFAULTS.answer(invocation);
            }
            return Arrays.stream(returnType.getEnumConstants())
                    .filter(candidate -> candidate.toString().equals(outcomeName))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(
                            "missing UserRepository.insertUser outcome " + outcomeName
                    ));
        });
    }

    private UserRegistrationApplicationService service(
            UserRepository repository,
            UserPolicyEventPublisher eventPublisher
    ) {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        return new UserRegistrationApplicationService(
                repository,
                new UserRegistrationDomainService(clock, new PasswordPolicyDomainService()),
                new UuidV7Generator(clock),
                eventPublisher
        );
    }

    private CreateVerifiedRegistrationUserCommand command(
            UUID userId,
            String username,
            String email,
            String headerUrl
    ) {
        return new CreateVerifiedRegistrationUserCommand(
                userId,
                username,
                ENCODED_PASSWORD,
                email,
                headerUrl
        );
    }

    private UserAccount account(
            UUID userId,
            String username,
            String email,
            String headerUrl,
            int type,
            int status,
            long policyVersion,
            long securityVersion
    ) {
        return new UserAccount(
                userId,
                username,
                ENCODED_PASSWORD,
                "",
                email,
                type,
                status,
                headerUrl,
                Date.from(NOW),
                null,
                null,
                policyVersion,
                securityVersion
        );
    }

    private UUID userId(int tail) {
        return UUID.fromString("018f7f66-3f8f-7a5a-8e32-" + String.format("%012x", tail));
    }
}
