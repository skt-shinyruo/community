package com.nowcoder.community.user.service;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.user.api.model.UserGrowthProfileView;
import com.nowcoder.community.user.api.model.UserProfileView;
import com.nowcoder.community.user.api.model.UserSummaryView;
import com.nowcoder.community.user.entity.User;
import com.nowcoder.community.user.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;
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
    void getSummaryByIdShouldRejectNonPositiveUserId() {
        UserQueryService service = new UserQueryService(userMapper);

        assertThatThrownBy(() -> service.getSummaryById(0))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException businessException = (BusinessException) ex;
                    assertThat(businessException.getErrorCode()).isEqualTo(INVALID_ARGUMENT);
                    assertThat(businessException.getMessage()).isEqualTo("userId 非法");
                });
    }

    @Test
    void getSummaryByIdShouldReturnNullWhenUserMissing() {
        UserQueryService service = new UserQueryService(userMapper);
        when(userMapper.selectById(7)).thenReturn(null);

        assertThat(service.getSummaryById(7)).isNull();
    }

    @Test
    void getSummaryByUsernameShouldReturnNullWhenUserMissing() {
        UserQueryService service = new UserQueryService(userMapper);
        when(userMapper.selectByName("ghost")).thenReturn(null);

        assertThat(service.getSummaryByUsername("ghost")).isNull();
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
    void findSummaryByEmailOrNullShouldTrimAndDelegateToMapper() {
        UserQueryService service = new UserQueryService(userMapper);
        User user = user(9, "alice");
        user.setHeaderUrl("h1");
        user.setType(2);
        when(userMapper.selectByEmail("alice@example.com")).thenReturn(user);

        UserSummaryView found = service.findSummaryByEmailOrNull("  alice@example.com  ");

        assertThat(found).extracting(
                UserSummaryView::id,
                UserSummaryView::username,
                UserSummaryView::headerUrl,
                UserSummaryView::type
        ).containsExactly(9, "alice", "h1", 2);
        verify(userMapper).selectByEmail("alice@example.com");
    }

    @Test
    void listSummariesByIdsShouldIgnoreInvalidIdsAndProjectViews() {
        UserQueryService service = new UserQueryService(userMapper);
        when(userMapper.selectUserSummariesByIds(List.of(1, 2)))
                .thenReturn(List.of(user(1, "alice"), user(2, "bob")));

        assertThat(service.listSummariesByIds(List.of(0, 1, 2, 2)))
                .extracting(UserSummaryView::id)
                .containsExactly(1, 2);
    }

    @Test
    void getGrowthProfileShouldProjectScoreLevelAndUserFields() {
        UserQueryService service = new UserQueryService(userMapper);
        User user = user(5, "alice");
        user.setScore(250);
        user.setEmail("alice@example.com");
        user.setStatus(1);
        user.setHeaderUrl("h5");
        when(userMapper.selectById(5)).thenReturn(user);

        UserGrowthProfileView profile = service.getGrowthProfile(5);

        assertThat(profile).extracting(
                UserGrowthProfileView::userId,
                UserGrowthProfileView::username,
                UserGrowthProfileView::score,
                UserGrowthProfileView::level,
                UserGrowthProfileView::email,
                UserGrowthProfileView::status,
                UserGrowthProfileView::headerUrl
        ).containsExactly(5, "alice", 250, 3, "alice@example.com", 1, "h5");
    }

    @Test
    void getProfileShouldProjectFullProfileView() {
        UserQueryService service = new UserQueryService(userMapper);
        User user = user(6, "bob");
        Date createTime = new Date();
        user.setHeaderUrl("h6");
        user.setType(2);
        user.setStatus(1);
        user.setScore(120);
        user.setCreateTime(createTime);
        when(userMapper.selectById(6)).thenReturn(user);

        UserProfileView profile = service.getProfile(6);

        assertThat(profile).extracting(
                UserProfileView::userId,
                UserProfileView::username,
                UserProfileView::headerUrl,
                UserProfileView::type,
                UserProfileView::status,
                UserProfileView::createTime,
                UserProfileView::score,
                UserProfileView::level
        ).containsExactly(6, "bob", "h6", 2, 1, createTime, 120, 2);
    }

    private User user(int id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        return user;
    }
}
