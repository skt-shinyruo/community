package com.nowcoder.community.user.service;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.user.dto.AdminUserResponse;
import com.nowcoder.community.user.dto.UpdateUserRoleRequest;
import com.nowcoder.community.user.entity.User;
import com.nowcoder.community.user.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;
import java.util.UUID;

import static com.nowcoder.community.common.exception.CommonErrorCode.FORBIDDEN;
import static com.nowcoder.community.common.exception.CommonErrorCode.INTERNAL_ERROR;
import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    private static final UUID ACTOR_ID = UUID.fromString("00000000-0000-7000-8000-000000000099");
    private static final UUID TARGET_ID = UUID.fromString("00000000-0000-7000-8000-000000000008");
    private static final UUID SEARCH_ID = UUID.fromString("00000000-0000-7000-8000-000000000009");

    @Mock
    private UserMapper userMapper;

    @Test
    void searchShouldRejectWhenNoSelectorProvided() {
        AdminUserService service = new AdminUserService(userMapper);

        assertThatThrownBy(() -> service.search(null, " ", " "))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException businessException = (BusinessException) ex;
                    assertThat(businessException.getErrorCode()).isEqualTo(INVALID_ARGUMENT);
                    assertThat(businessException.getMessage()).isEqualTo("请提供 userId/username/email 之一");
                });
    }

    @Test
    void searchShouldTrimUsernameAndProjectAdminResponse() {
        AdminUserService service = new AdminUserService(userMapper);
        Date createTime = new Date();
        User user = user(UUID.fromString("00000000-0000-7000-8000-000000000007"), "alice", "alice@example.com", 2, 0, "h7", createTime);
        when(userMapper.selectByName("alice")).thenReturn(user);

        AdminUserResponse response = service.search(null, "  alice  ", null);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(UUID.fromString("00000000-0000-7000-8000-000000000007"));
        assertThat(response.getUsername()).isEqualTo("alice");
        assertThat(response.getEmail()).isEqualTo("alice@example.com");
        assertThat(response.getType()).isEqualTo(2);
        assertThat(response.getStatus()).isEqualTo(0);
        assertThat(response.getHeaderUrl()).isEqualTo("h7");
        assertThat(response.getCreateTime()).isEqualTo(createTime);
        verify(userMapper).selectByName("alice");
    }

    @Test
    void searchShouldReturnNullWhenTargetUserMissing() {
        AdminUserService service = new AdminUserService(userMapper);
        when(userMapper.selectById(SEARCH_ID)).thenReturn(null);

        assertThat(service.search(SEARCH_ID, null, null)).isNull();
    }

    @Test
    void updateRoleShouldRejectWhenConfirmMissing() {
        AdminUserService service = new AdminUserService(userMapper);
        UpdateUserRoleRequest request = roleRequest(TARGET_ID, 1, "elevate", false);

        assertThatThrownBy(() -> service.updateRole(ACTOR_ID, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException businessException = (BusinessException) ex;
                    assertThat(businessException.getErrorCode()).isEqualTo(INVALID_ARGUMENT);
                    assertThat(businessException.getMessage()).isEqualTo("需要二次确认（confirm=true）");
                });
    }

    @Test
    void updateRoleShouldRejectBlankReason() {
        AdminUserService service = new AdminUserService(userMapper);
        UpdateUserRoleRequest request = roleRequest(TARGET_ID, 1, "   ", true);

        assertThatThrownBy(() -> service.updateRole(ACTOR_ID, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException businessException = (BusinessException) ex;
                    assertThat(businessException.getErrorCode()).isEqualTo(INVALID_ARGUMENT);
                    assertThat(businessException.getMessage()).isEqualTo("reason 不能为空");
                });
    }

    @Test
    void updateRoleShouldRejectWhenTargetUserMissing() {
        AdminUserService service = new AdminUserService(userMapper);
        UpdateUserRoleRequest request = roleRequest(TARGET_ID, 1, "elevate", true);
        when(userMapper.selectById(TARGET_ID)).thenReturn(null);

        assertThatThrownBy(() -> service.updateRole(ACTOR_ID, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException businessException = (BusinessException) ex;
                    assertThat(businessException.getErrorCode()).isEqualTo(INVALID_ARGUMENT);
                    assertThat(businessException.getMessage()).isEqualTo("目标用户不存在");
                });
    }

    @Test
    void updateRoleShouldRejectAdminSelfDowngrade() {
        AdminUserService service = new AdminUserService(userMapper);
        UpdateUserRoleRequest request = roleRequest(TARGET_ID, 2, "handover", true);
        when(userMapper.selectById(TARGET_ID)).thenReturn(user(TARGET_ID, "admin", "admin@example.com", 1, 0, "h8", new Date()));

        assertThatThrownBy(() -> service.updateRole(TARGET_ID, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException businessException = (BusinessException) ex;
                    assertThat(businessException.getErrorCode()).isEqualTo(FORBIDDEN);
                    assertThat(businessException.getMessage()).isEqualTo("不允许降级自己的管理员权限");
                });
    }

    @Test
    void updateRoleShouldReturnWithoutWriteWhenRoleUnchanged() {
        AdminUserService service = new AdminUserService(userMapper);
        UpdateUserRoleRequest request = roleRequest(TARGET_ID, 1, "noop", true);
        when(userMapper.selectById(TARGET_ID)).thenReturn(user(TARGET_ID, "admin", "admin@example.com", 1, 0, "h8", new Date()));

        service.updateRole(ACTOR_ID, request);

        verify(userMapper, never()).updateType(TARGET_ID, 1);
    }

    @Test
    void updateRoleShouldPersistRoleChange() {
        AdminUserService service = new AdminUserService(userMapper);
        UpdateUserRoleRequest request = roleRequest(TARGET_ID, 2, "delegate moderation", true);
        when(userMapper.selectById(TARGET_ID)).thenReturn(user(TARGET_ID, "admin", "admin@example.com", 1, 0, "h8", new Date()));
        when(userMapper.updateType(TARGET_ID, 2)).thenReturn(1);

        service.updateRole(ACTOR_ID, request);

        verify(userMapper).updateType(TARGET_ID, 2);
    }

    @Test
    void updateRoleShouldFailWhenUpdateDoesNotAffectRows() {
        AdminUserService service = new AdminUserService(userMapper);
        UpdateUserRoleRequest request = roleRequest(TARGET_ID, 2, "delegate moderation", true);
        when(userMapper.selectById(TARGET_ID)).thenReturn(user(TARGET_ID, "admin", "admin@example.com", 1, 0, "h8", new Date()));
        when(userMapper.updateType(TARGET_ID, 2)).thenReturn(0);

        assertThatThrownBy(() -> service.updateRole(ACTOR_ID, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException businessException = (BusinessException) ex;
                    assertThat(businessException.getErrorCode()).isEqualTo(INTERNAL_ERROR);
                    assertThat(businessException.getMessage()).isEqualTo("更新用户角色失败");
                });
    }

    private UpdateUserRoleRequest roleRequest(UUID targetUserId, int type, String reason, boolean confirm) {
        UpdateUserRoleRequest request = new UpdateUserRoleRequest();
        request.setTargetUserId(targetUserId);
        request.setType(type);
        request.setReason(reason);
        request.setConfirm(confirm);
        return request;
    }

    private User user(UUID id, String username, String email, int type, int status, String headerUrl, Date createTime) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setEmail(email);
        user.setType(type);
        user.setStatus(status);
        user.setHeaderUrl(headerUrl);
        user.setCreateTime(createTime);
        return user;
    }
}
