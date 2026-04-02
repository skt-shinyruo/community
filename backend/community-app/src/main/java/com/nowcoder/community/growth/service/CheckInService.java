package com.nowcoder.community.growth.service;

import com.nowcoder.community.growth.entity.GrowthCheckIn;
import com.nowcoder.community.growth.event.GrowthEventPublisher;
import com.nowcoder.community.growth.event.payload.CheckInPayload;
import com.nowcoder.community.growth.mapper.GrowthCheckInMapper;
import com.nowcoder.community.wallet.api.action.WalletRewardActionApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

@Service
public class CheckInService {

    private static final long DAILY_WALLET_REWARD = 7L;
    private static final String GRANT_TYPE = "DailyCheckIn";

    private final GrowthCheckInMapper growthCheckInMapper;
    private final WalletRewardActionApi walletRewardActionApi;
    private final GrowthEventPublisher growthEventPublisher;

    public CheckInService(GrowthCheckInMapper growthCheckInMapper, WalletRewardActionApi walletRewardActionApi) {
        this(growthCheckInMapper, walletRewardActionApi, null);
    }

    @Autowired
    public CheckInService(GrowthCheckInMapper growthCheckInMapper, WalletRewardActionApi walletRewardActionApi, GrowthEventPublisher growthEventPublisher) {
        this.growthCheckInMapper = growthCheckInMapper;
        this.walletRewardActionApi = walletRewardActionApi;
        this.growthEventPublisher = growthEventPublisher;
    }

    @Transactional
    public CheckInResult checkIn(int userId, LocalDate bizDate) {
        GrowthCheckIn existing = growthCheckInMapper.selectByUserAndDateForUpdate(userId, bizDate);
        if (existing != null) {
            CheckInStatus status = status(userId, bizDate);
            return new CheckInResult(false, true, status.currentStreak(), status.maxStreak(), status.totalCheckInDays());
        }

        GrowthCheckIn previous = growthCheckInMapper.selectLatestBeforeDateForUpdate(userId, bizDate);
        int streakCount = previous != null && previous.getBizDate() != null && previous.getBizDate().plusDays(1).equals(bizDate)
                ? previous.getStreakCount() + 1
                : 1;

        try {
            growthCheckInMapper.insert(userId, bizDate, streakCount);
        } catch (DataIntegrityViolationException e) {
            GrowthCheckIn duplicated = growthCheckInMapper.selectByUserAndDateForUpdate(userId, bizDate);
            if (duplicated != null) {
                CheckInStatus status = status(userId, bizDate);
                return new CheckInResult(false, true, status.currentStreak(), status.maxStreak(), status.totalCheckInDays());
            }
            throw e;
        }
        walletRewardActionApi.issue(grantId(userId, bizDate), userId, DAILY_WALLET_REWARD, GRANT_TYPE);
        if (growthEventPublisher != null) {
            CheckInPayload payload = new CheckInPayload();
            payload.setUserId(userId);
            payload.setBizDate(bizDate);
            growthEventPublisher.publishCheckInCompleted(grantId(userId, bizDate), payload);
        }

        CheckInStatus status = status(userId, bizDate);
        return new CheckInResult(true, true, status.currentStreak(), status.maxStreak(), status.totalCheckInDays());
    }

    public CheckInStatus status(int userId, LocalDate bizDate) {
        GrowthCheckIn today = growthCheckInMapper.selectByUserAndDate(userId, bizDate);
        GrowthCheckIn latest = growthCheckInMapper.selectLatestByUserId(userId);
        int currentStreak = today != null ? today.getStreakCount() : streakForDate(latest, bizDate);
        int totalDays = growthCheckInMapper.countByUserId(userId);
        Integer maxStreak = growthCheckInMapper.maxStreakByUserId(userId);
        return new CheckInStatus(today != null, currentStreak, maxStreak == null ? 0 : maxStreak, totalDays);
    }

    public List<LocalDate> calendar(int userId, int year, int month) {
        YearMonth yearMonth = YearMonth.of(year, month);
        return growthCheckInMapper.selectBizDatesBetween(userId, yearMonth.atDay(1), yearMonth.atEndOfMonth());
    }

    private int streakForDate(GrowthCheckIn latest, LocalDate bizDate) {
        if (latest == null || latest.getBizDate() == null) {
            return 0;
        }
        return latest.getBizDate().plusDays(1).equals(bizDate) ? latest.getStreakCount() : 0;
    }

    private String grantId(int userId, LocalDate bizDate) {
        return "check-in:" + userId + ":" + String.valueOf(bizDate);
    }

    public record CheckInResult(boolean newlyCheckedIn, boolean checkedInToday, int currentStreak, int maxStreak, int totalCheckInDays) {
    }

    public record CheckInStatus(boolean checkedInToday, int currentStreak, int maxStreak, int totalCheckInDays) {
    }
}
