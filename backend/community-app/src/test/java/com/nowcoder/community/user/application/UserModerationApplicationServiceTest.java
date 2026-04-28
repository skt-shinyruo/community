package com.nowcoder.community.user.application;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.user.application.command.ApplyUserModerationCommand;
import com.nowcoder.community.user.domain.event.UserPolicyEventPublisher;
import com.nowcoder.community.user.domain.model.UserAccount;
import com.nowcoder.community.user.domain.model.UserModerationStatus;
import com.nowcoder.community.user.domain.repository.UserRepository;
import com.nowcoder.community.user.domain.service.UserModerationDomainService;
import com.nowcoder.community.user.exception.UserErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserModerationApplicationServiceTest {

    private static final UUID ZERO_UUID = new UUID(0L, 0L);
    private static final UUID USER_ID_3 = UUID.fromString("00000000-0000-7000-8000-000000000003");
    private static final UUID USER_ID_4 = UUID.fromString("00000000-0000-7000-8000-000000000004");
    private static final UUID USER_ID_7 = UUID.fromString("00000000-0000-7000-8000-000000000007");
    private static final Instant EXISTING_MUTE = Instant.parse("2026-03-27T10:15:30Z");
    private static final Instant EXISTING_BAN = Instant.parse("2026-03-28T10:15:30Z");

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserPolicyEventPublisher userPolicyEventPublisher;

    @Test
    void getModerationStateShouldProjectMuteAndBanTimestamps() {
        UserModerationApplicationService service = service();
        when(userRepository.findById(USER_ID_7)).thenReturn(Optional.of(account(USER_ID_7, EXISTING_MUTE, EXISTING_BAN)));

        UserModerationStatus status = service.getModerationState(USER_ID_7);

        assertThat(status.userId()).isEqualTo(USER_ID_7);
        assertThat(status.muteUntil()).isEqualTo(EXISTING_MUTE);
        assertThat(status.banUntil()).isEqualTo(EXISTING_BAN);
        verifyNoInteractions(userPolicyEventPublisher);
    }

    @Test
    void scanModerationStatesAfterIdShouldClampInputsAndSkipInvalidRows() {
        UserModerationApplicationService service = service();
        when(userRepository.scanModerationStatesAfterId(ZERO_UUID, 500)).thenReturn(Arrays.asList(
                null,
                new UserModerationStatus(null, null, null),
                new UserModerationStatus(USER_ID_3, EXISTING_MUTE, null),
                new UserModerationStatus(USER_ID_4, null, null)
        ));

        List<UserModerationStatus> statuses = service.scanModerationStatesAfterId(null, 999);

        assertThat(statuses)
                .extracting(UserModerationStatus::userId)
                .containsExactly(USER_ID_3, USER_ID_4);
        assertThat(statuses.get(0).muteUntil()).isEqualTo(EXISTING_MUTE);
        assertThat(statuses.get(1).muteUntil()).isNull();
        verify(userRepository).scanModerationStatesAfterId(ZERO_UUID, 500);
    }

    @Test
    void applyModerationShouldMuteUserPersistStatusAndPublishPolicyEvent() {
        UserModerationApplicationService service = service();
        when(userRepository.findById(USER_ID_7)).thenReturn(Optional.of(account(USER_ID_7, null, EXISTING_BAN)));

        Instant before = Instant.now();
        UserModerationStatus status = service.applyModeration(new ApplyUserModerationCommand(USER_ID_7, " mute ", 120));
        Instant after = Instant.now();

        assertThat(status.userId()).isEqualTo(USER_ID_7);
        assertThat(status.muteUntil()).isBetween(before.plusSeconds(120), after.plusSeconds(120));
        assertThat(status.banUntil()).isEqualTo(EXISTING_BAN);
        verify(userRepository).updateModerationUntil(USER_ID_7, status.muteUntil(), EXISTING_BAN);

        ArgumentCaptor<UserModerationStatus> statusCaptor = ArgumentCaptor.forClass(UserModerationStatus.class);
        ArgumentCaptor<Instant> occurredAtCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(userPolicyEventPublisher).publishUserPolicyChanged(statusCaptor.capture(), occurredAtCaptor.capture());
        assertThat(statusCaptor.getValue()).isEqualTo(status);
        assertThat(occurredAtCaptor.getValue()).isBetween(before, after);
    }

    @Test
    void applyModerationShouldRejectBlankActionBeforeLoadingUser() {
        UserModerationApplicationService service = service();

        Throwable thrown = catchThrowable(() -> service.applyModeration(
                new ApplyUserModerationCommand(USER_ID_7, " ", 60)
        ));

        assertThat(thrown).isInstanceOf(BusinessException.class)
                .hasMessage("action 不能为空");
        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(INVALID_ARGUMENT);
        verifyNoInteractions(userRepository, userPolicyEventPublisher);
    }

    @Test
    void applyModerationShouldRejectMissingUser() {
        UserModerationApplicationService service = service();
        when(userRepository.findById(USER_ID_7)).thenReturn(Optional.empty());

        Throwable thrown = catchThrowable(() -> service.applyModeration(
                new ApplyUserModerationCommand(USER_ID_7, "mute", 60)
        ));

        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(UserErrorCode.USER_NOT_FOUND);
        verify(userRepository, never()).updateModerationUntil(eq(USER_ID_7), eq(EXISTING_MUTE), eq(EXISTING_BAN));
        verifyNoInteractions(userPolicyEventPublisher);
    }

    private UserModerationApplicationService service() {
        return new UserModerationApplicationService(
                userRepository,
                new UserModerationDomainService(),
                userPolicyEventPublisher
        );
    }

    private static UserAccount account(UUID userId, Instant muteUntil, Instant banUntil) {
        return new UserAccount(
                userId,
                "u-" + userId,
                "encoded",
                "salt",
                "u@example.com",
                0,
                0,
                "h",
                Date.from(Instant.now().minus(Duration.ofMinutes(1))),
                0,
                muteUntil,
                banUntil
        );
    }
}
