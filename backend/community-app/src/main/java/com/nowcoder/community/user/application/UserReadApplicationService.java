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
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

import static com.nowcoder.community.user.exception.UserErrorCode.USER_NOT_FOUND;

@Service
public class UserReadApplicationService {

    private final UserRepository userRepository;
    private final UserReadDomainService userReadDomainService;
    private final WalletAccountQueryApi walletAccountQueryApi;

    public UserReadApplicationService(
            UserRepository userRepository,
            UserReadDomainService userReadDomainService,
            WalletAccountQueryApi walletAccountQueryApi
    ) {
        this.userRepository = userRepository;
        this.userReadDomainService = userReadDomainService;
        this.walletAccountQueryApi = walletAccountQueryApi;
    }

    public UserSummaryResult getSummaryById(UUID userId) {
        userReadDomainService.assertValidUserId(userId);
        return userRepository.findById(userId)
                .map(this::toSummaryResult)
                .orElse(null);
    }

    public UserSummaryResult getSummaryByUsername(String username) {
        String value = userReadDomainService.normalizeUsername(username);
        return userRepository.findByUsername(value)
                .map(this::toSummaryResult)
                .orElse(null);
    }

    public UserSummaryResult findSummaryByEmailOrNull(String email) {
        String value = userReadDomainService.normalizeEmail(email);
        return userRepository.findByEmail(value)
                .map(this::toSummaryResult)
                .orElse(null);
    }

    public List<UserSummaryResult> listSummariesByIds(List<UUID> userIds) {
        return userRepository.listSummariesByIds(normalizeUserIds(userIds)).stream()
                .map(this::toSummaryResult)
                .toList();
    }

    public List<UserSummaryResult> listSummaryResultsByIds(List<UUID> rawUserIds) {
        List<UUID> ids = normalizeUserIds(rawUserIds);
        if (ids.isEmpty()) {
            return List.of();
        }
        return userRepository.listSummariesByIds(ids).stream()
                .map(this::toSummaryResult)
                .toList();
    }

    public UserProfileResult getProfile(UUID userId) {
        userReadDomainService.assertValidUserId(userId);
        UserProfile profile = userRepository.findProfileById(userId)
                .orElseThrow(() -> new BusinessException(USER_NOT_FOUND));
        return new UserProfileResult(
                profile.id(),
                profile.username(),
                profile.headerUrl(),
                profile.type(),
                profile.status(),
                profile.createTime(),
                walletAccountQueryApi.balanceOfUser(profile.id()),
                walletAccountQueryApi.statusOfUser(profile.id())
        );
    }

    public void requireExistingUser(UUID userId) {
        userReadDomainService.assertValidUserId(userId);
        if (userRepository.findById(userId).isEmpty()) {
            throw new BusinessException(USER_NOT_FOUND);
        }
    }

    private List<UUID> normalizeUserIds(List<UUID> rawUserIds) {
        if (rawUserIds == null || rawUserIds.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<UUID> dedup = new LinkedHashSet<>();
        for (UUID userId : rawUserIds) {
            if (userId == null) {
                continue;
            }
            dedup.add(userId);
            if (dedup.size() >= 200) {
                break;
            }
        }
        return new ArrayList<>(dedup);
    }

    private UserSummaryResult toSummaryResult(UserAccount user) {
        if (user == null || user.id() == null) {
            return null;
        }
        return new UserSummaryResult(user.id(), user.username(), user.headerUrl(), user.type());
    }

    private UserSummaryResult toSummaryResult(UserSummary user) {
        if (user == null || user.id() == null) {
            return null;
        }
        return new UserSummaryResult(user.id(), user.username(), user.headerUrl(), user.type());
    }
}
