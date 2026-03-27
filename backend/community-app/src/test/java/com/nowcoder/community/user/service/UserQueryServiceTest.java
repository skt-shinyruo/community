package com.nowcoder.community.user.service;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.user.entity.User;
import com.nowcoder.community.user.exception.UserErrorCode;
import com.nowcoder.community.user.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserQueryServiceTest {

    @Mock
    private UserMapper userMapper;

    @Test
    void getByIdShouldThrowNotFoundWhenUserMissing() {
        UserQueryService service = new UserQueryService(userMapper);
        when(userMapper.selectById(7)).thenReturn(null);

        assertThatThrownBy(() -> service.getById(7))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(UserErrorCode.USER_NOT_FOUND);
    }

    @Test
    void getByUsernameShouldRejectBlankUsername() {
        UserQueryService service = new UserQueryService(userMapper);

        assertThatThrownBy(() -> service.getByUsername("  "))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException businessException = (BusinessException) ex;
                    assertThat(businessException.getErrorCode()).isEqualTo(INVALID_ARGUMENT);
                    assertThat(businessException.getMessage()).isEqualTo("username 不能为空");
                });
    }

    @Test
    void findByEmailOrNullShouldTrimAndDelegateToMapper() {
        UserQueryService service = new UserQueryService(userMapper);
        User user = user(9, "alice");
        when(userMapper.selectByEmail("alice@example.com")).thenReturn(user);

        User found = service.findByEmailOrNull("  alice@example.com  ");

        assertThat(found).isSameAs(user);
        verify(userMapper).selectByEmail("alice@example.com");
    }

    @Test
    void listUserSummariesByIdsShouldIgnoreInvalidIdsAndPreserveMapperRows() {
        UserQueryService service = new UserQueryService(userMapper);
        when(userMapper.selectUserSummariesByIds(List.of(1, 2)))
                .thenReturn(List.of(user(1, "alice"), user(2, "bob")));

        assertThat(service.listUserSummariesByIds(List.of(0, 1, 2, 2)))
                .extracting(User::getId)
                .containsExactly(1, 2);
    }

    private User user(int id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        return user;
    }
}
