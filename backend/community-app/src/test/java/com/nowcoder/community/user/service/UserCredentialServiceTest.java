package com.nowcoder.community.user.service;

import com.nowcoder.community.auth.exception.AuthErrorCode;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.user.api.model.UserCredentialView;
import com.nowcoder.community.user.entity.User;
import com.nowcoder.community.user.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;
import static com.nowcoder.community.user.exception.UserErrorCode.USER_NOT_FOUND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserCredentialServiceTest {

    @Mock
    private UserMapper userMapper;

    @Test
    void authenticateShouldRejectBlankCredentials() {
        UserCredentialService service = new UserCredentialService(userMapper);

        assertThatThrownBy(() -> service.authenticate("  ", "secret"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(AuthErrorCode.INVALID_CREDENTIALS);

        verifyNoInteractions(userMapper);
    }

    @Test
    void authenticateShouldRejectDisabledUser() {
        UserCredentialService service = new UserCredentialService(userMapper);
        when(userMapper.selectByName("alice")).thenReturn(disabledUser("alice"));

        assertThatThrownBy(() -> service.authenticate("alice", "pw"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(AuthErrorCode.USER_DISABLED);
    }

    @Test
    void authenticateShouldUpgradeLegacyPasswordHashOnSuccessfulMatch() {
        UserCredentialService service = new UserCredentialService(userMapper);
        User user = activeUser(7, "alice");
        user.setSalt("abc");
        user.setPassword(md5("secretabc"));
        when(userMapper.selectByName("alice")).thenReturn(user);

        UserCredentialView authenticated = service.authenticate("alice", "secret");

        ArgumentCaptor<String> passwordCaptor = ArgumentCaptor.forClass(String.class);
        verify(userMapper).updatePassword(eq(7), passwordCaptor.capture());
        assertThat(authenticated).extracting(
                UserCredentialView::userId,
                UserCredentialView::username,
                UserCredentialView::status,
                UserCredentialView::type
        ).containsExactly(7, "alice", 1, 0);
        assertThat(new BCryptPasswordEncoder().matches("secret", passwordCaptor.getValue())).isTrue();
        assertThat(user.getPassword()).isEqualTo(passwordCaptor.getValue());
    }

    @Test
    void getByUserIdShouldProjectCredentialView() {
        UserCredentialService service = new UserCredentialService(userMapper);
        User user = activeUser(7, "alice");
        user.setHeaderUrl("h7");
        when(userMapper.selectById(7)).thenReturn(user);

        UserCredentialView credential = service.getByUserId(7);

        assertThat(credential).extracting(
                UserCredentialView::userId,
                UserCredentialView::username,
                UserCredentialView::status,
                UserCredentialView::type,
                UserCredentialView::headerUrl
        ).containsExactly(7, "alice", 1, 0, "h7");
    }

    @Test
    void getByUserIdShouldRejectMissingUser() {
        UserCredentialService service = new UserCredentialService(userMapper);
        when(userMapper.selectById(7)).thenReturn(null);

        assertThatThrownBy(() -> service.getByUserId(7))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(USER_NOT_FOUND);
    }

    @Test
    void updatePasswordShouldRejectMissingUser() {
        UserCredentialService service = new UserCredentialService(userMapper);
        when(userMapper.selectById(7)).thenReturn(null);

        assertThatThrownBy(() -> service.updatePassword(7, "secret"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(USER_NOT_FOUND);
    }

    @Test
    void updatePasswordShouldPersistBcryptHashForExistingUser() {
        UserCredentialService service = new UserCredentialService(userMapper);
        when(userMapper.selectById(7)).thenReturn(activeUser(7, "alice"));
        when(userMapper.updatePassword(eq(7), org.mockito.ArgumentMatchers.anyString())).thenReturn(1);

        service.updatePassword(7, "  secret  ");

        ArgumentCaptor<String> passwordCaptor = ArgumentCaptor.forClass(String.class);
        verify(userMapper).updatePassword(eq(7), passwordCaptor.capture());
        assertThat(new BCryptPasswordEncoder().matches("secret", passwordCaptor.getValue())).isTrue();
    }

    @Test
    void authoritiesOfShouldMapUserTypesToExpectedRoles() {
        UserCredentialService service = new UserCredentialService(userMapper);
        UserCredentialView admin = new UserCredentialView(1, "admin", 1, 1, "h1");
        UserCredentialView moderator = new UserCredentialView(2, "mod", 1, 2, "h2");
        UserCredentialView regular = new UserCredentialView(3, "user", 1, 0, "h3");

        assertThat(service.authoritiesOf((UserCredentialView) null)).isEmpty();
        assertThat(service.authoritiesOf(admin)).isEqualTo(List.of("ROLE_ADMIN"));
        assertThat(service.authoritiesOf(moderator)).isEqualTo(List.of("ROLE_MODERATOR"));
        assertThat(service.authoritiesOf(regular)).isEqualTo(List.of("ROLE_USER"));
    }

    @Test
    void updatePasswordShouldRejectBlankPassword() {
        UserCredentialService service = new UserCredentialService(userMapper);

        assertThatThrownBy(() -> service.updatePassword(7, "  "))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException businessException = (BusinessException) ex;
                    assertThat(businessException.getErrorCode()).isEqualTo(INVALID_ARGUMENT);
                    assertThat(businessException.getMessage()).isEqualTo("newPassword 不能为空");
                });
    }

    private User activeUser(int id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setStatus(1);
        user.setType(0);
        return user;
    }

    private User disabledUser(String username) {
        User user = activeUser(7, username);
        user.setStatus(0);
        return user;
    }

    private String md5(String input) {
        return DigestUtils.md5DigestAsHex(input.getBytes(StandardCharsets.UTF_8));
    }
}
