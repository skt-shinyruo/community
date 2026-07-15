package com.nowcoder.community.user.application;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.user.application.command.UpdateUserRoleCommand;
import com.nowcoder.community.user.application.port.UserAuditLogPort;
import com.nowcoder.community.user.application.result.AdminUserResult;
import com.nowcoder.community.user.domain.model.UserAccount;
import com.nowcoder.community.user.domain.repository.UserRepository;
import com.nowcoder.community.user.domain.service.UserRoleDomainService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;
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

    @Mock
    private UserAuditLogPort userAuditLogPort;

    @Test
    void searchShouldRejectWhenNoSelectorProvided() {
        AdminUserApplicationService service = service();

        Throwable thrown = catchThrowable(() -> service.search(null, " ", " "));

        assertThat(thrown).isInstanceOf(BusinessException.class)
                .hasMessage("请提供 userId/username/email 之一");
        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(INVALID_ARGUMENT);
        verifyNoInteractions(userRepository, userAuditLogPort);
    }

    @Test
    void searchShouldTrimUsernameAndProjectAdminResult() {
        AdminUserApplicationService service = service();
        Date createTime = new Date();
        UserAccount user = user(uuid(7), "alice", "alice@example.com", 2, 0, "h7", createTime);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        AdminUserResult result = service.search(null, "  alice  ", null);

        assertThat(result).isNotNull();
        assertThat(result).extracting(
                AdminUserResult::id,
                AdminUserResult::username,
                AdminUserResult::email,
                AdminUserResult::type,
                AdminUserResult::status,
                AdminUserResult::headerUrl,
                AdminUserResult::createTime
        ).containsExactly(uuid(7), "alice", "alice@example.com", 2, 0, "h7", createTime);
        verify(userRepository).findByUsername("alice");
    }

    @Test
    void searchShouldReturnNullWhenTargetUserMissing() {
        AdminUserApplicationService service = service();
        when(userRepository.findById(SEARCH_ID)).thenReturn(Optional.empty());

        AdminUserResult result = service.search(SEARCH_ID, null, null);

        assertThat(result).isNull();
    }

    @Test
    void updateRoleShouldRejectMissingTargetUser() {
        AdminUserApplicationService service = service();
        UpdateUserRoleCommand command = new UpdateUserRoleCommand(ACTOR_ID, TARGET_ID, 1, "elevate", true);
        when(userRepository.findById(TARGET_ID)).thenReturn(Optional.empty());

        Throwable thrown = catchThrowable(() -> service.updateRole(command));

        assertThat(thrown).isInstanceOf(BusinessException.class)
                .hasMessage("目标用户不存在");
        verify(userRepository).findById(TARGET_ID);
        verify(userRepository, never()).updateRole(any(), anyInt(), anyLong());
        verifyNoInteractions(userAuditLogPort);
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
        UpdateUserRoleCommand command = new UpdateUserRoleCommand(ACTOR_ID, TARGET_ID, 1, "noop", true);
        when(userRepository.findById(TARGET_ID)).thenReturn(Optional.of(user(TARGET_ID, "admin", "admin@example.com", 1, 0, "h8", new Date())));

        service.updateRole(command);

        verify(userRepository, never()).updateRole(any(), anyInt(), anyLong());
        verifyNoInteractions(userAuditLogPort);
    }

    @Test
    void updateRoleShouldPersistRoleChangeAndWriteAuditLog() {
        AdminUserApplicationService service = service();
        UpdateUserRoleCommand command = new UpdateUserRoleCommand(ACTOR_ID, TARGET_ID, 2, "  delegate moderation  ", true);
        when(userRepository.findById(TARGET_ID)).thenReturn(Optional.of(user(TARGET_ID, "admin", "admin@example.com", 1, 0, "h8", new Date())));
        when(userRepository.nextUserSecurityVersion(TARGET_ID)).thenReturn(123L);

        service.updateRole(command);

        InOrder inOrder = inOrder(userRepository, userAuditLogPort);
        inOrder.verify(userRepository).findById(TARGET_ID);
        inOrder.verify(userRepository).nextUserSecurityVersion(TARGET_ID);
        inOrder.verify(userRepository).updateRole(TARGET_ID, 2, 123L);
        inOrder.verify(userAuditLogPort).recordRoleUpdated(ACTOR_ID, TARGET_ID, 1, 2, "delegate moderation");
    }

    private AdminUserApplicationService service() {
        return new AdminUserApplicationService(userRepository, new UserRoleDomainService(), userAuditLogPort);
    }

    private static UserAccount user(UUID id, String username, String email, int type, int status, String headerUrl, Date createTime) {
        return new UserAccount(id, username, "pw", "salt", email, type, status, headerUrl, createTime, null, null, 0L, 0L);
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }
}
