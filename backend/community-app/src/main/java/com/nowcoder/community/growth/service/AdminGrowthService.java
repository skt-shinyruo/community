package com.nowcoder.community.growth.service;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.growth.dto.AdminAdjustBalanceRequest;
import com.nowcoder.community.growth.dto.AdminGrowthUserResponse;
import com.nowcoder.community.growth.entity.AdminRewardAdjustment;
import com.nowcoder.community.growth.entity.RewardLedgerEntry;
import com.nowcoder.community.growth.exception.GrowthErrorCode;
import com.nowcoder.community.growth.mapper.AdminRewardAdjustmentMapper;
import com.nowcoder.community.growth.mapper.RewardLedgerMapper;
import com.nowcoder.community.user.entity.User;
import com.nowcoder.community.user.mapper.UserMapper;
import com.nowcoder.community.user.service.PointsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.UUID;

@Service
public class AdminGrowthService {

    private static final int DEFAULT_LEDGER_LIMIT = 5;

    private final UserMapper userMapper;
    private final PointsService pointsService;
    private final RewardAccountService rewardAccountService;
    private final RewardLedgerMapper rewardLedgerMapper;
    private final AdminRewardAdjustmentMapper adminRewardAdjustmentMapper;

    public AdminGrowthService(
            UserMapper userMapper,
            PointsService pointsService,
            RewardAccountService rewardAccountService,
            RewardLedgerMapper rewardLedgerMapper,
            AdminRewardAdjustmentMapper adminRewardAdjustmentMapper
    ) {
        this.userMapper = userMapper;
        this.pointsService = pointsService;
        this.rewardAccountService = rewardAccountService;
        this.rewardLedgerMapper = rewardLedgerMapper;
        this.adminRewardAdjustmentMapper = adminRewardAdjustmentMapper;
    }

    public AdminGrowthUserResponse search(Integer userId, String username, String email) {
        User user = resolveUser(userId, username, email);
        if (user == null) {
            return null;
        }

        AdminGrowthUserResponse response = new AdminGrowthUserResponse();
        response.setUserId(user.getId());
        response.setUsername(user.getUsername());
        response.setEmail(user.getEmail());
        response.setScore(user.getScore());
        response.setLevel(pointsService.levelForScore(user.getScore()));
        response.setRewardBalance(rewardAccountService.availableBalanceOf(user.getId()));
        response.setFrozenBalance(rewardAccountService.frozenBalanceOf(user.getId()));
        response.setRecentRewardLedgers(rewardLedgerMapper.selectRecentByUserId(user.getId(), DEFAULT_LEDGER_LIMIT));
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

        User target = userMapper.selectById(request.getTargetUserId());
        if (target == null || target.getId() <= 0) {
            throw new BusinessException(GrowthErrorCode.TARGET_USER_NOT_FOUND, "target user not found");
        }

        String assetType = request.getAssetType() == null ? "" : request.getAssetType().trim();
        int beforeValue;
        int afterValue;
        if ("SCORE".equals(assetType)) {
            beforeValue = target.getScore();
            pointsService.applyPoints(
                    target.getId(),
                    "admin-adjust:" + UUID.randomUUID(),
                    "AdminGrowthAdjust",
                    request.getDelta()
            );
            afterValue = userMapper.selectById(target.getId()).getScore();
        } else if ("REWARD_BALANCE".equals(assetType)) {
            beforeValue = rewardAccountService.availableBalanceOf(target.getId());
            rewardAccountService.applyAvailableDelta(
                    target.getId(),
                    "admin-adjust:" + UUID.randomUUID(),
                    "AdminRewardAdjust",
                    request.getDelta(),
                    "growth-admin",
                    reason
            );
            afterValue = rewardAccountService.availableBalanceOf(target.getId());
        } else {
            throw new BusinessException(GrowthErrorCode.INVALID_REQUEST, "unsupported asset type");
        }

        AdminRewardAdjustment adjustment = new AdminRewardAdjustment();
        adjustment.setActorUserId(actorUserId);
        adjustment.setTargetUserId(target.getId());
        adjustment.setAssetType(assetType);
        adjustment.setDelta(request.getDelta());
        adjustment.setBeforeValue(beforeValue);
        adjustment.setAfterValue(afterValue);
        adjustment.setReason(reason);
        adjustment.setConfirmToken("confirmed");
        adminRewardAdjustmentMapper.insert(adjustment);

        return search(target.getId(), null, null);
    }

    private User resolveUser(Integer userId, String username, String email) {
        if (userId != null && userId > 0) {
            return userMapper.selectById(userId);
        }
        if (StringUtils.hasText(username)) {
            return userMapper.selectByName(username.trim());
        }
        if (StringUtils.hasText(email)) {
            return userMapper.selectByEmail(email.trim());
        }
        throw new BusinessException(GrowthErrorCode.INVALID_REQUEST, "userId/username/email required");
    }

    private int positiveLimit(int limit) {
        return limit > 0 ? limit : DEFAULT_LEDGER_LIMIT;
    }
}
