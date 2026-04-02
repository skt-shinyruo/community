package com.nowcoder.community.growth.service;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.growth.api.model.LegacyRewardAccountView;
import com.nowcoder.community.growth.api.query.LegacyRewardAccountQueryApi;
import com.nowcoder.community.growth.entity.RewardAccount;
import com.nowcoder.community.growth.exception.GrowthErrorCode;
import com.nowcoder.community.growth.mapper.RewardAccountMapper;
import com.nowcoder.community.growth.mapper.RewardLedgerMapper;
import com.nowcoder.community.wallet.api.action.WalletRewardActionApi;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RewardAccountService implements LegacyRewardAccountQueryApi {

    private final RewardAccountMapper rewardAccountMapper;
    private final RewardLedgerMapper rewardLedgerMapper;
    private final WalletRewardActionApi walletRewardActionApi;

    public RewardAccountService(RewardAccountMapper rewardAccountMapper,
                                RewardLedgerMapper rewardLedgerMapper,
                                WalletRewardActionApi walletRewardActionApi) {
        this.rewardAccountMapper = rewardAccountMapper;
        this.rewardLedgerMapper = rewardLedgerMapper;
        this.walletRewardActionApi = walletRewardActionApi;
    }

    @Override
    public LegacyRewardAccountView getLegacyRewardAccount(int userId) {
        RewardAccount account = rewardAccountMapper.selectByUserId(userId);
        if (account == null) {
            return null;
        }
        return new LegacyRewardAccountView(userId, account.getAvailableBalance(), account.getFrozenBalance());
    }

    public int availableBalanceOf(int userId) {
        RewardAccount account = rewardAccountMapper.selectByUserId(userId);
        return account == null ? 0 : account.getAvailableBalance();
    }

    public int frozenBalanceOf(int userId) {
        RewardAccount account = rewardAccountMapper.selectByUserId(userId);
        return account == null ? 0 : account.getFrozenBalance();
    }

    @Transactional
    public void creditAvailable(int userId, String eventId, String eventType, int delta, String sourceModule, String remark) {
        applyAvailableDelta(userId, eventId, eventType, delta, sourceModule, remark);
    }

    @Transactional
    public void issueToWallet(int userId, String eventId, String eventType, int amount, String sourceModule, String remark) {
        if (amount <= 0) {
            throw new BusinessException(GrowthErrorCode.INVALID_REQUEST, "wallet reward amount must be positive");
        }
        walletRewardActionApi.issue(eventId, userId, amount, eventType);
    }

    @Transactional
    public void applyAvailableDelta(int userId, String eventId, String eventType, int delta, String sourceModule, String remark) {
        ensureAccountExists(userId);
        int updated = rewardAccountMapper.addAvailableBalance(userId, delta);
        if (updated != 1) {
            throw new BusinessException(GrowthErrorCode.REWARD_BALANCE_INSUFFICIENT, "reward balance insufficient: userId=" + userId);
        }
        RewardAccount accountAfter = rewardAccountMapper.selectByUserId(userId);
        rewardLedgerMapper.insert(
                userId,
                eventId,
                eventType,
                delta,
                accountAfter.getAvailableBalance(),
                accountAfter.getFrozenBalance(),
                sourceModule,
                remark
        );
    }

    @Transactional
    public void moveAvailableToFrozen(int userId, String eventId, String eventType, int amount, String sourceModule, String remark) {
        ensureAccountExists(userId);
        if (amount <= 0) {
            throw new BusinessException(GrowthErrorCode.INVALID_REQUEST, "freeze amount must be positive");
        }
        int updated = rewardAccountMapper.moveAvailableToFrozen(userId, amount);
        if (updated != 1) {
            throw new BusinessException(GrowthErrorCode.REWARD_BALANCE_INSUFFICIENT, "reward balance insufficient: userId=" + userId);
        }
        RewardAccount accountAfter = rewardAccountMapper.selectByUserId(userId);
        rewardLedgerMapper.insert(
                userId,
                eventId,
                eventType,
                -amount,
                accountAfter.getAvailableBalance(),
                accountAfter.getFrozenBalance(),
                sourceModule,
                remark
        );
    }

    @Transactional
    public void moveFrozenToAvailable(int userId, String eventId, String eventType, int amount, String sourceModule, String remark) {
        ensureAccountExists(userId);
        if (amount <= 0) {
            throw new BusinessException(GrowthErrorCode.INVALID_REQUEST, "release amount must be positive");
        }
        int updated = rewardAccountMapper.moveFrozenToAvailable(userId, amount);
        if (updated != 1) {
            throw new BusinessException(GrowthErrorCode.REWARD_FROZEN_BALANCE_INSUFFICIENT, "reward frozen balance insufficient: userId=" + userId);
        }
        RewardAccount accountAfter = rewardAccountMapper.selectByUserId(userId);
        rewardLedgerMapper.insert(
                userId,
                eventId,
                eventType,
                amount,
                accountAfter.getAvailableBalance(),
                accountAfter.getFrozenBalance(),
                sourceModule,
                remark
        );
    }

    @Transactional
    public void deductFrozenBalance(int userId, String eventId, String eventType, int amount, String sourceModule, String remark) {
        ensureAccountExists(userId);
        if (amount <= 0) {
            throw new BusinessException(GrowthErrorCode.INVALID_REQUEST, "deduct frozen amount must be positive");
        }
        int updated = rewardAccountMapper.deductFrozenBalance(userId, amount);
        if (updated != 1) {
            throw new BusinessException(GrowthErrorCode.REWARD_FROZEN_BALANCE_INSUFFICIENT, "reward frozen balance insufficient: userId=" + userId);
        }
        RewardAccount accountAfter = rewardAccountMapper.selectByUserId(userId);
        rewardLedgerMapper.insert(
                userId,
                eventId,
                eventType,
                0,
                accountAfter.getAvailableBalance(),
                accountAfter.getFrozenBalance(),
                sourceModule,
                remark
        );
    }

    private void ensureAccountExists(int userId) {
        if (rewardAccountMapper.selectByUserId(userId) != null) {
            return;
        }
        try {
            rewardAccountMapper.insertAccount(userId);
        } catch (DataIntegrityViolationException ignored) {
            // Another request created the row first; treat as successful lazy init.
        }
    }
}
