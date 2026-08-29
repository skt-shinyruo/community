package com.nowcoder.community.user.application;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.user.domain.model.UserAccount;
import com.nowcoder.community.user.domain.repository.UserRepository;
import com.nowcoder.community.user.domain.service.UserRoleDomainService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUserApplicationServiceTest {

    private static final UUID ACTOR_ID = uuid(99);
    private static final UUID TARGET_ID = uuid(8);
    private static final UUID SEARCH_ID = uuid(9);

    @Mock
    private UserRepository userRepository;

    @Test
    void searchShouldRejectWhenNoSelectorProvided() {
        AdminUserApplicationService service = service();

        Throwable thrown = catchThrowable(() -> service.search(null, " ", " "));

        assertThat(thrown).isInstanceOf(BusinessException.class)
                .hasMessage("请提供 userId/username/email 之一");
        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(INVALID_ARGUMENT);
        verifyNoInteractions(userRepository);
    }

    @Test
    void searchShouldTrimUsernameAndProjectAdminResult() {
        AdminUserApplicationService service = service();
        Date createTime = new Date();
        UserAccount user = user(uuid(7), "alice", "alice@example.com", 2, 0, "h7", createTime);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        AdminUserApplicationService.AdminUserResult result = service.search(null, "  alice  ", null);

        assertThat(result).isNotNull();
        assertThat(result).extracting(
                AdminUserApplicationService.AdminUserResult::id,
                AdminUserApplicationService.AdminUserResult::username,
                AdminUserApplicationService.AdminUserResult::email,
                AdminUserApplicationService.AdminUserResult::type,
                AdminUserApplicationService.AdminUserResult::status,
                AdminUserApplicationService.AdminUserResult::headerUrl,
                AdminUserApplicationService.AdminUserResult::createTime
        ).containsExactly(uuid(7), "alice", "alice@example.com", 2, 0, "h7", createTime);
        verify(userRepository).findByUsername("alice");
    }

    @Test
    void searchShouldReturnNullWhenTargetUserMissing() {
        AdminUserApplicationService service = service();
        when(userRepository.findById(SEARCH_ID)).thenReturn(Optional.empty());

        AdminUserApplicationService.AdminUserResult result = service.search(SEARCH_ID, null, null);

        assertThat(result).isNull();
    }

    @Test
    void updateRoleShouldRejectMissingTargetUser() {
        AdminUserApplicationService service = service();
        AdminUserApplicationService.UpdateRoleCommand command = command(1, "elevate");
        when(userRepository.findByIdForUpdate(ACTOR_ID)).thenReturn(Optional.of(activeAdmin(ACTOR_ID)));
        when(userRepository.findByIdForUpdate(TARGET_ID)).thenReturn(Optional.empty());

        Throwable thrown = catchThrowable(() -> service.updateRole(command));

        assertThat(thrown).isInstanceOf(BusinessException.class)
                .hasMessage("目标用户不存在");
        verify(userRepository).lockRoleManagement();
        verify(userRepository).findByIdForUpdate(ACTOR_ID);
        verify(userRepository).findByIdForUpdate(TARGET_ID);
        verify(userRepository, never()).updateRole(any(), anyInt(), anyLong());
    }

    @Test
    void updateRoleShouldRejectNullCommand() {
        AdminUserApplicationService service = service();

        assertThatThrownBy(() -> service.updateRole(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("command must not be null");
    }

    @Test
    void updateRoleShouldReturnWithoutWriteWhenRoleUnchanged() {
        AdminUserApplicationService service = service();
        AdminUserApplicationService.UpdateRoleCommand command = command(1, "noop");
        when(userRepository.findByIdForUpdate(ACTOR_ID)).thenReturn(Optional.of(activeAdmin(ACTOR_ID)));
        when(userRepository.findByIdForUpdate(TARGET_ID)).thenReturn(Optional.of(user(TARGET_ID, "admin", "admin@example.com", 1, 1, "h8", new Date())));

        service.updateRole(command);

        verify(userRepository, never()).updateRole(any(), anyInt(), anyLong());
    }

    @Test
    void updateRoleShouldPersistRoleChangeAndWriteAuditLog() {
        AdminUserApplicationService service = service();
        AdminUserApplicationService.UpdateRoleCommand command = command(2, "  delegate moderation  ");
        when(userRepository.findByIdForUpdate(ACTOR_ID)).thenReturn(Optional.of(activeAdmin(ACTOR_ID)));
        when(userRepository.findByIdForUpdate(TARGET_ID)).thenReturn(Optional.of(user(TARGET_ID, "admin", "admin@example.com", 1, 1, "h8", new Date())));
        when(userRepository.nextUserSecurityVersion(TARGET_ID)).thenReturn(123L);

        service.updateRole(command);

        InOrder inOrder = inOrder(userRepository);
        inOrder.verify(userRepository).lockRoleManagement();
        inOrder.verify(userRepository).findByIdForUpdate(ACTOR_ID);
        inOrder.verify(userRepository).findByIdForUpdate(TARGET_ID);
        inOrder.verify(userRepository).nextUserSecurityVersion(TARGET_ID);
        inOrder.verify(userRepository).updateRole(TARGET_ID, 2, 123L);
    }

    @Test
    void updateRoleShouldReauthorizeActorAfterAcquiringRoleManagementLock() {
        AdminUserApplicationService service = service();
        AdminUserApplicationService.UpdateRoleCommand command = command(2, "delegate");
        when(userRepository.findByIdForUpdate(ACTOR_ID)).thenReturn(Optional.of(
                user(ACTOR_ID, "former-admin", "former@example.com", 2, 1, "ha", new Date())
        ));

        assertThatThrownBy(() -> service.updateRole(command))
                .isInstanceOf(BusinessException.class)
                .hasMessage("操作者不再具备有效管理员权限");

        InOrder inOrder = inOrder(userRepository);
        inOrder.verify(userRepository).lockRoleManagement();
        inOrder.verify(userRepository).findByIdForUpdate(ACTOR_ID);
        verify(userRepository, never()).findByIdForUpdate(TARGET_ID);
        verify(userRepository, never()).updateRole(any(), anyInt(), anyLong());
    }

    private AdminUserApplicationService service() {
        return new AdminUserApplicationService(
                userRepository,
                new UserRoleDomainService(),
                Clock.systemUTC()
        );
    }

    private AdminUserApplicationService.UpdateRoleCommand command(int type, String reason) {
        return new AdminUserApplicationService.UpdateRoleCommand(ACTOR_ID, TARGET_ID, type, reason, true);
    }

    private static UserAccount activeAdmin(UUID id) {
        return user(id, "actor-admin", "actor@example.com", 1, 1, "ha", new Date());
    }

    private static UserAccount user(UUID id, String username, String email, int type, int status, String headerUrl, Date createTime) {
        return new UserAccount(id, username, "pw", "salt", email, type, status, headerUrl, createTime, null, null, 0L, 0L);
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }
}
