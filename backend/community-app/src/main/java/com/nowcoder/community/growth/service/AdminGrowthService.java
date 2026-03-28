package com.nowcoder.community.growth.service;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.growth.dto.AdminAdjustBalanceRequest;
import com.nowcoder.community.growth.dto.AdminGrowthUserResponse;
import com.nowcoder.community.growth.entity.AdminRewardAdjustment;
import com.nowcoder.community.growth.entity.RewardLedgerEntry;
import com.nowcoder.community.growth.exception.GrowthErrorCode;
import com.nowcoder.community.growth.mapper.AdminRewardAdjustmentMapper;
import com.nowcoder.community.growth.mapper.RewardLedgerMapper;
import com.nowcoder.community.user.api.action.UserPointsActionApi;
import com.nowcoder.community.user.api.model.UserGrowthProfileView;
import com.nowcoder.community.user.api.model.UserSummaryView;
import com.nowcoder.community.user.api.query.UserLookupQueryApi;
import com.nowcoder.community.user.api.query.UserProfileQueryApi;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.UUID;

@Service
public class AdminGrowthService {

    private static final int DEFAULT_LEDGER_LIMIT = 5;

    private final UserLookupQueryApi userLookupQueryApi;
    private final UserProfileQueryApi userProfileQueryApi;
    private final UserPointsActionApi userPointsActionApi;
    private final RewardAccountService rewardAccountService;
    private final RewardLedgerMapper rewardLedgerMapper;
    private final AdminRewardAdjustmentMapper adminRewardAdjustmentMapper;

    public AdminGrowthService(
            UserLookupQueryApi userLookupQueryApi,
            UserProfileQueryApi userProfileQueryApi,
            UserPointsActionApi userPointsActionApi,
            RewardAccountService rewardAccountService,
            RewardLedgerMapper rewardLedgerMapper,
            AdminRewardAdjustmentMapper adminRewardAdjustmentMapper
    ) {
        this.userLookupQueryApi = userLookupQueryApi;
        this.userProfileQueryApi = userProfileQueryApi;
        this.userPointsActionApi = userPointsActionApi;
        this.rewardAccountService = rewardAccountService;
        this.rewardLedgerMapper = rewardLedgerMapper;
        this.adminRewardAdjustmentMapper = adminRewardAdjustmentMapper;
    }

    public AdminGrowthUserResponse search(Integer userId, String username, String email) {
        UserSummaryView summary = resolveUser(userId, username, email);
        if (summary == null) {
            return null;
        }
        UserGrowthProfileView profile = userProfileQueryApi.getGrowthProfile(summary.id());

        AdminGrowthUserResponse response = new AdminGrowthUserResponse();
        response.setUserId(profile.userId());
        response.setUsername(profile.username());
        response.setEmail(profile.email());
        response.setScore(profile.score());
        response.setLevel(profile.level());
        response.setRewardBalance(rewardAccountService.availableBalanceOf(profile.userId()));
        response.setFrozenBalance(rewardAccountService.frozenBalanceOf(profile.userId()));
        response.setRecentRewardLedgers(rewardLedgerMapper.selectRecentByUserId(profile.userId(), DEFAULT_LEDGER_LIMIT));
        return response;
    }

    public List<RewardLedgerEntry> recentRewardLedgers(int userId, int limit) {
        return rewardLedgerMapper.selectRecentByUserId(userId, positiveLimit(limit));
    }

    public List<AdminRewardAdjustment> recentAdjustments(int userId, int limit) {
        return adminRewardAdjustmentMapper.selectRecentByTargetUserId(userId, positiveLimit(limit));
    }

    @Transactional
    public AdminGrowthUserResponse adjust(int actorUserId, AdminAdjustBalanceRequest request) {
        if (request == null) {
            throw new BusinessException(GrowthErrorCode.INVALID_REQUEST, "adjust request required");
        }
        if (!request.isConfirm()) {
            throw new BusinessException(GrowthErrorCode.INVALID_REQUEST, "confirm=true required");
        }
        String reason = request.getReason() == null ? "" : request.getReason().trim();
        if (!StringUtils.hasText(reason)) {
            throw new BusinessException(GrowthErrorCode.INVALID_REQUEST, "reason required");
        }

        UserSummaryView target = findSummaryByIdOrNull(request.getTargetUserId());
        if (target == null || target.id() <= 0) {
            throw new BusinessException(GrowthErrorCode.TARGET_USER_NOT_FOUND, "target user not found");
        }

        String assetType = request.getAssetType() == null ? "" : request.getAssetType().trim();
        int beforeValue;
        int afterValue;
        if ("SCORE".equals(assetType)) {
            beforeValue = userProfileQueryApi.getGrowthProfile(target.id()).score();
            userPointsActionApi.applyPoints(
                    target.id(),
                    "admin-adjust:" + UUID.randomUUID(),
                    "AdminGrowthAdjust",
                    request.getDelta()
            );
            afterValue = userProfileQueryApi.getGrowthProfile(target.id()).score();
        } else if ("REWARD_BALANCE".equals(assetType)) {
            beforeValue = rewardAccountService.availableBalanceOf(target.id());
            rewardAccountService.applyAvailableDelta(
                    target.id(),
                    "admin-adjust:" + UUID.randomUUID(),
                    "AdminRewardAdjust",
                    request.getDelta(),
                    "growth-admin",
                    reason
            );
            afterValue = rewardAccountService.availableBalanceOf(target.id());
        } else {
            throw new BusinessException(GrowthErrorCode.INVALID_REQUEST, "unsupported asset type");
        }

        AdminRewardAdjustment adjustment = new AdminRewardAdjustment();
        adjustment.setActorUserId(actorUserId);
        adjustment.setTargetUserId(target.id());
        adjustment.setAssetType(assetType);
        adjustment.setDelta(request.getDelta());
        adjustment.setBeforeValue(beforeValue);
        adjustment.setAfterValue(afterValue);
        adjustment.setReason(reason);
        adjustment.setConfirmToken("confirmed");
        adminRewardAdjustmentMapper.insert(adjustment);

        return search(target.id(), null, null);
    }

    private UserSummaryView resolveUser(Integer userId, String username, String email) {
        if (userId != null && userId > 0) {
            return findSummaryByIdOrNull(userId);
        }
        if (StringUtils.hasText(username)) {
            return findSummaryByUsernameOrNull(username.trim());
        }
        if (StringUtils.hasText(email)) {
            return userLookupQueryApi.findSummaryByEmailOrNull(email.trim());
        }
        throw new BusinessException(GrowthErrorCode.INVALID_REQUEST, "userId/username/email required");
    }

    private UserSummaryView findSummaryByIdOrNull(int userId) {
        return userLookupQueryApi.getSummaryById(userId);
    }

    private UserSummaryView findSummaryByUsernameOrNull(String username) {
        return userLookupQueryApi.getSummaryByUsername(username);
    }

    private int positiveLimit(int limit) {
        return limit > 0 ? limit : DEFAULT_LEDGER_LIMIT;
    }
}
