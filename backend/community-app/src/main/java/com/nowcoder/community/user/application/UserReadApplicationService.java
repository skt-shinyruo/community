package com.nowcoder.community.user.application;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.user.api.model.UserProfileView;
import com.nowcoder.community.user.api.model.UserSummaryView;
import com.nowcoder.community.user.api.query.UserLookupQueryApi;
import com.nowcoder.community.user.api.query.UserProfileQueryApi;
import com.nowcoder.community.user.domain.model.UserAccount;
import com.nowcoder.community.user.domain.model.UserProfile;
import com.nowcoder.community.user.domain.model.UserSummary;
import com.nowcoder.community.user.domain.repository.UserRepository;
import com.nowcoder.community.user.domain.service.UserReadDomainService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

import static com.nowcoder.community.user.exception.UserErrorCode.USER_NOT_FOUND;

@Service
public class UserReadApplicationService implements UserLookupQueryApi, UserProfileQueryApi {

    private final UserRepository userRepository;
    private final UserReadDomainService userReadDomainService;
    public UserReadApplicationService(
            UserRepository userRepository,
            UserReadDomainService userReadDomainService
    ) {
        this.userRepository = userRepository;
        this.userReadDomainService = userReadDomainService;
    }

    @Override
    public UserSummaryView getSummaryById(UUID userId) {
        userReadDomainService.assertValidUserId(userId);
        return userRepository.findById(userId)
                .map(this::toSummaryView)
                .orElse(null);
    }

    @Override
    public UserSummaryView getSummaryByUsername(String username) {
        String value = userReadDomainService.normalizeUsername(username);
        return userRepository.findByUsername(value)
                .map(this::toSummaryView)
                .orElse(null);
    }

    @Override
    public UserSummaryView findSummaryByEmailOrNull(String email) {
        String value = userReadDomainService.normalizeEmail(email);
        return userRepository.findByEmail(value)
                .map(this::toSummaryView)
                .orElse(null);
    }

    @Override
    public List<UserSummaryView> listSummariesByIds(List<UUID> userIds) {
        return userRepository.listSummariesByIds(normalizeUserIds(userIds)).stream()
                .map(this::toSummaryView)
                .toList();
    }

    @Override
    public UserProfileView getProfile(UUID userId) {
        userReadDomainService.assertValidUserId(userId);
        UserProfile profile = userRepository.findProfileById(userId)
                .orElseThrow(() -> new BusinessException(USER_NOT_FOUND));
        return new UserProfileView(
                profile.id(),
                profile.username(),
                profile.headerUrl(),
                profile.type(),
                profile.status(),
                profile.createTime()
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

    private UserSummaryView toSummaryView(UserAccount user) {
        if (user == null || user.id() == null) {
            return null;
        }
        return new UserSummaryView(user.id(), user.username(), user.headerUrl(), user.type());
    }

    private UserSummaryView toSummaryView(UserSummary user) {
        if (user == null || user.id() == null) {
            return null;
        }
        return new UserSummaryView(user.id(), user.username(), user.headerUrl(), user.type());
    }
}
