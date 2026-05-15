package com.nowcoder.community.user.application;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.user.application.result.UserProfileResult;
import com.nowcoder.community.user.application.result.UserSummaryResult;
import com.nowcoder.community.user.domain.model.UserAccount;
import com.nowcoder.community.user.domain.model.UserProfile;
import com.nowcoder.community.user.domain.model.UserSummary;
import com.nowcoder.community.user.domain.repository.UserRepository;
import com.nowcoder.community.user.domain.service.UserReadDomainService;
import com.nowcoder.community.wallet.api.query.WalletAccountQueryApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserReadApplicationServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private WalletAccountQueryApi walletAccountQueryApi;

    @Test
    void getProfileShouldProjectBaseUserAndWalletState() {
        UserReadApplicationService service = new UserReadApplicationService(
                userRepository,
                new UserReadDomainService(),
                walletAccountQueryApi
        );
        UUID userId = uuid(7);
        Date createTime = new Date();
        when(userRepository.findProfileById(userId))
                .thenReturn(Optional.of(new UserProfile(userId, "alice", "h7", 2, 1, createTime)));
        when(walletAccountQueryApi.balanceOfUser(userId)).thenReturn(900L);
        when(walletAccountQueryApi.statusOfUser(userId)).thenReturn("ACTIVE");

        UserProfileResult profile = service.getProfile(userId);

        assertThat(profile).extracting(
                UserProfileResult::userId,
                UserProfileResult::username,
                UserProfileResult::headerUrl,
                UserProfileResult::type,
                UserProfileResult::status,
                UserProfileResult::createTime,
                UserProfileResult::walletBalance,
                UserProfileResult::walletStatus
        ).containsExactly(userId, "alice", "h7", 2, 1, createTime, 900L, "ACTIVE");
    }

    @Test
    void listSummaryResultsByIdsShouldDeduplicateCapAndPreserveOrder() {
        UserReadApplicationService service = new UserReadApplicationService(
                userRepository,
                new UserReadDomainService(),
                walletAccountQueryApi
        );
        UUID aliceId = uuid(7);
        UUID bobId = uuid(9);
        UUID tailId = uuid(11);
        List<UUID> raw = Arrays.asList(aliceId, bobId, aliceId, null, tailId);
        when(userRepository.listSummariesByIds(List.of(aliceId, bobId, tailId)))
                .thenReturn(List.of(
                        new UserSummary(aliceId, "alice", "h7", 1),
                        new UserSummary(bobId, "bob", "h9", 2),
                        new UserSummary(tailId, "tail", "h11", 3)
                ));

        List<UserSummaryResult> result = service.listSummaryResultsByIds(raw);

        assertThat(result).extracting(UserSummaryResult::id).containsExactly(aliceId, bobId, tailId);
        assertThat(result).extracting(UserSummaryResult::username).containsExactly("alice", "bob", "tail");
        verify(userRepository).listSummariesByIds(List.of(aliceId, bobId, tailId));
    }

    @Test
    void requireExistingUserShouldUseLightweightLookup() {
        UserReadApplicationService service = new UserReadApplicationService(
                userRepository,
                new UserReadDomainService(),
                walletAccountQueryApi
        );
        UUID userId = uuid(7);
        when(userRepository.findById(userId)).thenReturn(Optional.of(account(userId, "alice", "h7", 1)));

        service.requireExistingUser(userId);

        verify(userRepository).findById(userId);
        verify(userRepository, never()).findProfileById(userId);
    }

    @Test
    void getSummaryByIdShouldRejectNullUserId() {
        UserReadApplicationService service = new UserReadApplicationService(
                userRepository,
                new UserReadDomainService(),
                walletAccountQueryApi
        );

        assertThatThrownBy(() -> service.getSummaryById(null))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException businessException = (BusinessException) ex;
                    assertThat(businessException.getErrorCode()).isEqualTo(INVALID_ARGUMENT);
                    assertThat(businessException.getMessage()).isEqualTo("userId 非法");
                });
    }

    private static UserAccount account(UUID id, String username, String headerUrl, int type) {
        return new UserAccount(id, username, "encoded", "", username + "@example.com", type, 1, headerUrl, new Date(), null, null);
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }
}
