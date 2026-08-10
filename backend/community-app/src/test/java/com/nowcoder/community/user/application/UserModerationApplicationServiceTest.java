package com.nowcoder.community.user.application;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.user.api.action.UserModerationActionApi.ApplyModerationCommand;
import com.nowcoder.community.user.api.model.UserModerationStateView;
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
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;
import static com.nowcoder.community.common.exception.CommonErrorCode.FORBIDDEN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
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
    private static final UUID ADMIN_ID = UUID.fromString("00000000-0000-7000-8000-000000000101");
    private static final UUID MODERATOR_ID = UUID.fromString("00000000-0000-7000-8000-000000000102");
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

        UserModerationStateView status = service.getModerationState(USER_ID_7);

        assertThat(status.userId()).isEqualTo(USER_ID_7);
        assertThat(status.muteUntil()).isEqualTo(EXISTING_MUTE);
        assertThat(status.banUntil()).isEqualTo(EXISTING_BAN);
        assertThat(status.version()).isEqualTo(100L);
        verifyNoInteractions(userPolicyEventPublisher);
    }

    @Test
    void scanModerationStatesAtVersionAfterIdShouldClampInputsAndSkipInvalidRows() {
        UserModerationApplicationService service = service();
        when(userRepository.scanModerationStatesAtVersionAfterId(77L, ZERO_UUID, 500)).thenReturn(Arrays.asList(
                null,
                new UserModerationStatus(null, null, null, 0L),
                new UserModerationStatus(USER_ID_3, EXISTING_MUTE, null, 301L),
                new UserModerationStatus(USER_ID_4, null, null, 302L)
        ));

        List<UserModerationStateView> statuses = service.scanModerationStatesAtVersionAfterId(77L, null, 999);

        assertThat(statuses)
                .extracting(UserModerationStateView::userId)
                .containsExactly(USER_ID_3, USER_ID_4);
        assertThat(statuses.get(0).muteUntil()).isEqualTo(EXISTING_MUTE);
        assertThat(statuses.get(0).version()).isEqualTo(301L);
        assertThat(statuses.get(1).muteUntil()).isNull();
        assertThat(statuses.get(1).version()).isEqualTo(302L);
        verify(userRepository).scanModerationStatesAtVersionAfterId(77L, ZERO_UUID, 500);
    }

    @Test
    void applyModerationShouldMuteUserPersistStatusAndPublishPolicyEvent() {
        UserModerationApplicationService service = service();
        when(userRepository.findByIdForUpdate(ADMIN_ID)).thenReturn(Optional.of(account(ADMIN_ID, 1, 1, null, null)));
        when(userRepository.findByIdForUpdate(USER_ID_7)).thenReturn(Optional.of(account(USER_ID_7, null, EXISTING_BAN)));
        when(userRepository.nextUserPolicyVersion(USER_ID_7)).thenReturn(101L);

        Instant before = Instant.now();
        UserModerationStateView status = service.applyModeration(command(ADMIN_ID, USER_ID_7, " mute ", 120));
        Instant after = Instant.now();

        assertThat(status.userId()).isEqualTo(USER_ID_7);
        assertThat(status.muteUntil()).isBetween(before.plusSeconds(120), after.plusSeconds(120));
        assertThat(status.banUntil()).isEqualTo(EXISTING_BAN);
        assertThat(status.version()).isEqualTo(101L);
        verify(userRepository).nextUserPolicyVersion(USER_ID_7);
        verify(userRepository).updateModerationUntil(
                USER_ID_7,
                status.muteUntil(),
                EXISTING_BAN,
                101L,
                0L,
                100L
        );

        ArgumentCaptor<UserModerationStatus> statusCaptor = ArgumentCaptor.forClass(UserModerationStatus.class);
        ArgumentCaptor<Instant> occurredAtCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(userPolicyEventPublisher).publishUserPolicyChanged(statusCaptor.capture(), occurredAtCaptor.capture());
        assertThat(statusCaptor.getValue()).isEqualTo(new UserModerationStatus(
                status.userId(), status.muteUntil(), status.banUntil(), status.version()
        ));
        assertThat(occurredAtCaptor.getValue()).isBetween(before, after);
    }

    @Test
    void applyModerationShouldIncrementSecurityVersionAndPublishWhenBanBecomesActive() {
        UserModerationApplicationService service = service();
        when(userRepository.findByIdForUpdate(ADMIN_ID)).thenReturn(Optional.of(account(ADMIN_ID, 1, 1, null, null)));
        when(userRepository.findByIdForUpdate(USER_ID_7)).thenReturn(Optional.of(account(USER_ID_7, null, null)));
        when(userRepository.nextUserPolicyVersion(USER_ID_7)).thenReturn(101L);
        when(userRepository.nextUserSecurityVersion(USER_ID_7)).thenReturn(202L);

        Instant before = Instant.now();
        UserModerationStateView status = service.applyModeration(command(ADMIN_ID, USER_ID_7, "ban", 120));
        Instant after = Instant.now();

        assertThat(status.userId()).isEqualTo(USER_ID_7);
        assertThat(status.muteUntil()).isNull();
        assertThat(status.banUntil()).isBetween(before.plusSeconds(120), after.plusSeconds(120));
        assertThat(status.version()).isEqualTo(101L);

        var inOrder = inOrder(userRepository, userPolicyEventPublisher);
        inOrder.verify(userRepository).lockRoleManagement();
        inOrder.verify(userRepository).findByIdForUpdate(ADMIN_ID);
        inOrder.verify(userRepository).findByIdForUpdate(USER_ID_7);
        inOrder.verify(userRepository).nextUserSecurityVersion(USER_ID_7);
        inOrder.verify(userRepository).nextUserPolicyVersion(USER_ID_7);
        inOrder.verify(userRepository).updateModerationUntil(
                USER_ID_7,
                null,
                status.banUntil(),
                101L,
                202L,
                100L
        );
        inOrder.verify(userPolicyEventPublisher).publishUserPolicyChanged(eq(new UserModerationStatus(
                status.userId(), status.muteUntil(), status.banUntil(), status.version()
        )), any(Instant.class));
    }

    @Test
    void applyModerationShouldRejectBlankActionBeforeLoadingUser() {
        UserModerationApplicationService service = service();

        Throwable thrown = catchThrowable(() -> service.applyModeration(command(ADMIN_ID, USER_ID_7, " ", 60)));

        assertThat(thrown).isInstanceOf(BusinessException.class)
                .hasMessage("action 不能为空");
        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(INVALID_ARGUMENT);
        verifyNoInteractions(userRepository, userPolicyEventPublisher);
    }

    @Test
    void applyModerationShouldRejectMissingUser() {
        UserModerationApplicationService service = service();
        when(userRepository.findByIdForUpdate(ADMIN_ID)).thenReturn(Optional.of(account(ADMIN_ID, 1, 1, null, null)));
        when(userRepository.findByIdForUpdate(USER_ID_7)).thenReturn(Optional.empty());

        Throwable thrown = catchThrowable(() -> service.applyModeration(command(ADMIN_ID, USER_ID_7, "mute", 60)));

        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(UserErrorCode.USER_NOT_FOUND);
        verify(userRepository, never()).updateModerationUntil(
                eq(USER_ID_7),
                eq(EXISTING_MUTE),
                eq(EXISTING_BAN),
                anyLong(),
                anyLong(),
                anyLong()
        );
        verifyNoInteractions(userPolicyEventPublisher);
    }

    @Test
    void applyModerationShouldAllowModeratorToModerateOrdinaryUser() {
        UserModerationApplicationService service = service();
        when(userRepository.findByIdForUpdate(MODERATOR_ID))
                .thenReturn(Optional.of(account(MODERATOR_ID, 2, 1, null, null)));
        when(userRepository.findByIdForUpdate(USER_ID_7))
                .thenReturn(Optional.of(account(USER_ID_7, 0, 1, null, null)));
        when(userRepository.nextUserPolicyVersion(USER_ID_7)).thenReturn(101L);

        UserModerationStateView status = service.applyModeration(
                command(MODERATOR_ID, USER_ID_7, "mute", 60)
        );

        assertThat(status.muteUntil()).isNotNull();
        verify(userRepository).updateModerationUntil(
                eq(USER_ID_7),
                any(Instant.class),
                isNull(),
                eq(101L),
                eq(0L),
                eq(100L)
        );
    }

    @Test
    void applyModerationShouldRejectModeratorActingOnAdminOrPeer() {
        UserModerationApplicationService service = service();
        UserAccount moderator = account(MODERATOR_ID, 2, 1, null, null);
        when(userRepository.findByIdForUpdate(MODERATOR_ID)).thenReturn(Optional.of(moderator));
        when(userRepository.findByIdForUpdate(ADMIN_ID))
                .thenReturn(Optional.of(account(ADMIN_ID, 1, 1, null, null)));

        assertForbidden(
                () -> service.applyModeration(command(MODERATOR_ID, ADMIN_ID, "ban", 60)),
                "不允许处罚管理员"
        );

        UUID peerId = UUID.fromString("00000000-0000-7000-8000-000000000103");
        when(userRepository.findByIdForUpdate(peerId))
                .thenReturn(Optional.of(account(peerId, 2, 1, null, null)));

        assertForbidden(
                () -> service.applyModeration(command(MODERATOR_ID, peerId, "mute", 60)),
                "版主只能处罚普通用户"
        );
        verify(userRepository, never()).nextUserPolicyVersion(any());
        verifyNoInteractions(userPolicyEventPublisher);
    }

    @Test
    void applyModerationShouldRejectAdminTargetAndSelfModeration() {
        UserModerationApplicationService service = service();
        UserAccount admin = account(ADMIN_ID, 1, 1, null, null);
        when(userRepository.findByIdForUpdate(ADMIN_ID)).thenReturn(Optional.of(admin));

        assertForbidden(
                () -> service.applyModeration(command(ADMIN_ID, ADMIN_ID, "ban", 60)),
                "不允许处罚自己"
        );

        UUID otherAdminId = UUID.fromString("00000000-0000-7000-8000-000000000104");
        when(userRepository.findByIdForUpdate(otherAdminId))
                .thenReturn(Optional.of(account(otherAdminId, 1, 1, null, null)));

        assertForbidden(
                () -> service.applyModeration(command(ADMIN_ID, otherAdminId, "ban", 60)),
                "不允许处罚管理员"
        );
        verify(userRepository, never()).nextUserPolicyVersion(any());
        verifyNoInteractions(userPolicyEventPublisher);
    }

    @Test
    void applyModerationShouldRejectOrdinaryOrInactiveActor() {
        UserModerationApplicationService service = service();
        when(userRepository.findByIdForUpdate(USER_ID_3))
                .thenReturn(Optional.of(account(USER_ID_3, 0, 1, null, null)));

        assertForbidden(
                () -> service.applyModeration(command(USER_ID_3, USER_ID_7, "mute", 60)),
                "普通用户无权执行用户处罚"
        );

        when(userRepository.findByIdForUpdate(MODERATOR_ID))
                .thenReturn(Optional.of(account(MODERATOR_ID, 2, 0, null, null)));
        assertForbidden(
                () -> service.applyModeration(command(MODERATOR_ID, USER_ID_7, "mute", 60)),
                "操作者不再具备有效治理权限"
        );

        UUID bannedModeratorId = UUID.fromString("00000000-0000-7000-8000-000000000105");
        when(userRepository.findByIdForUpdate(bannedModeratorId)).thenReturn(Optional.of(account(
                bannedModeratorId,
                2,
                1,
                null,
                Instant.now().plusSeconds(60)
        )));
        assertForbidden(
                () -> service.applyModeration(command(bannedModeratorId, USER_ID_7, "mute", 60)),
                "操作者不再具备有效治理权限"
        );
        verify(userRepository, never()).findByIdForUpdate(USER_ID_7);
        verify(userRepository, never()).nextUserPolicyVersion(any());
        verifyNoInteractions(userPolicyEventPublisher);
    }

    private UserModerationApplicationService service() {
        return new UserModerationApplicationService(
                userRepository,
                new UserModerationDomainService(),
                userPolicyEventPublisher,
                Clock.systemUTC()
        );
    }

    private static UserAccount account(UUID userId, Instant muteUntil, Instant banUntil) {
        return account(userId, 0, 1, muteUntil, banUntil);
    }

    private static UserAccount account(UUID userId, int type, int status, Instant muteUntil, Instant banUntil) {
        return new UserAccount(
                userId,
                "u-" + userId,
                "encoded",
                "salt",
                "u@example.com",
                type,
                status,
                "h",
                Date.from(Instant.now().minus(Duration.ofMinutes(1))),
                muteUntil,
                banUntil,
                100L,
                0L
        );
    }

    private static ApplyModerationCommand command(
            UUID actorUserId,
            UUID targetUserId,
            String action,
            int durationSeconds
    ) {
        return new ApplyModerationCommand(actorUserId, targetUserId, action, durationSeconds);
    }

    private static void assertForbidden(org.assertj.core.api.ThrowableAssert.ThrowingCallable invocation, String message) {
        Throwable thrown = catchThrowable(invocation);
        assertThat(thrown).isInstanceOf(BusinessException.class).hasMessage(message);
        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(FORBIDDEN);
    }
}
