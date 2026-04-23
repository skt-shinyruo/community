package com.nowcoder.community.user.service;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.user.api.model.UserProfileView;
import com.nowcoder.community.user.api.model.UserSummaryView;
import com.nowcoder.community.user.entity.User;
import com.nowcoder.community.user.mapper.UserMapper;
import com.nowcoder.community.wallet.service.WalletAccountService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserQueryServiceTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private WalletAccountService walletAccountService;

    @Test
    void getSummaryByIdShouldRejectNullUserId() {
        UserQueryService service = new UserQueryService(userMapper, walletAccountService);

        assertThatThrownBy(() -> service.getSummaryById(null))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException businessException = (BusinessException) ex;
                    assertThat(businessException.getErrorCode()).isEqualTo(INVALID_ARGUMENT);
                    assertThat(businessException.getMessage()).isEqualTo("userId 非法");
                });
    }

    @Test
    void getSummaryByIdShouldReturnNullWhenUserMissing() {
        UserQueryService service = new UserQueryService(userMapper, walletAccountService);
        UUID userId = uuid(7);
        when(userMapper.selectById(userId)).thenReturn(null);

        assertThat(service.getSummaryById(userId)).isNull();
    }

    @Test
    void getSummaryByUsernameShouldReturnNullWhenUserMissing() {
        UserQueryService service = new UserQueryService(userMapper, walletAccountService);
        when(userMapper.selectByName("ghost")).thenReturn(null);

        assertThat(service.getSummaryByUsername("ghost")).isNull();
    }

    @Test
    void getByUsernameShouldRejectBlankUsername() {
        UserQueryService service = new UserQueryService(userMapper, walletAccountService);

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
        UserQueryService service = new UserQueryService(userMapper, walletAccountService);
        UUID userId = uuid(9);
        User user = user(userId, "alice");
        user.setHeaderUrl("h1");
        user.setType(2);
        when(userMapper.selectByEmail("alice@example.com")).thenReturn(user);

        UserSummaryView found = service.findSummaryByEmailOrNull("  alice@example.com  ");

        assertThat(found).extracting(
                UserSummaryView::id,
                UserSummaryView::username,
                UserSummaryView::headerUrl,
                UserSummaryView::type
        ).containsExactly(userId, "alice", "h1", 2);
        verify(userMapper).selectByEmail("alice@example.com");
    }

    @Test
    void listSummariesByIdsShouldIgnoreInvalidIdsAndProjectViews() {
        UserQueryService service = new UserQueryService(userMapper, walletAccountService);
        UUID aliceId = uuid(1);
        UUID bobId = uuid(2);
        when(userMapper.selectUserSummariesByIds(List.of(aliceId, bobId)))
                .thenReturn(List.of(user(aliceId, "alice"), user(bobId, "bob")));

        assertThat(service.listSummariesByIds(Arrays.asList(null, aliceId, bobId, bobId)))
                .extracting(UserSummaryView::id)
                .containsExactly(aliceId, bobId);
    }

    @Test
    void getProfileShouldProjectFullProfileView() {
        UserQueryService service = new UserQueryService(userMapper, walletAccountService);
        UUID userId = uuid(6);
        User user = user(userId, "bob");
        Date createTime = new Date();
        user.setHeaderUrl("h6");
        user.setType(2);
        user.setStatus(1);
        user.setScore(120);
        user.setCreateTime(createTime);
        when(userMapper.selectById(userId)).thenReturn(user);
        when(walletAccountService.balanceOfUser(userId)).thenReturn(520L);
        when(walletAccountService.statusOfUser(userId)).thenReturn("ACTIVE");

        UserProfileView profile = service.getProfile(userId);

        assertThat(profile).extracting(
                UserProfileView::userId,
                UserProfileView::username,
                UserProfileView::headerUrl,
                UserProfileView::type,
                UserProfileView::status,
                UserProfileView::createTime,
                UserProfileView::score,
                UserProfileView::level,
                UserProfileView::walletBalance,
                UserProfileView::walletStatus
        ).containsExactly(userId, "bob", "h6", 2, 1, createTime, 120, 2, 520L, "ACTIVE");
        verify(walletAccountService).balanceOfUser(userId);
        verify(walletAccountService).statusOfUser(userId);
    }

    private User user(UUID id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        return user;
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }
}
