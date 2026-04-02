package com.nowcoder.community.growth.service;

import com.nowcoder.community.growth.entity.GrowthCheckIn;
import com.nowcoder.community.growth.event.GrowthEventPublisher;
import com.nowcoder.community.growth.mapper.GrowthCheckInMapper;
import com.nowcoder.community.wallet.service.WalletRewardService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CheckInServiceUnitTest {

    @Mock
    private GrowthCheckInMapper growthCheckInMapper;

    @Mock
    private WalletRewardService walletRewardService;

    @Mock
    private GrowthEventPublisher growthEventPublisher;

    @Test
    void duplicateInsertRaceShouldReturnAlreadyCheckedInWithoutDuplicatingGrant() {
        CheckInService service = new CheckInService(growthCheckInMapper, walletRewardService, growthEventPublisher);
        LocalDate bizDate = LocalDate.of(2026, 3, 22);

        GrowthCheckIn current = new GrowthCheckIn();
        current.setUserId(1);
        current.setBizDate(bizDate);
        current.setStreakCount(3);

        when(growthCheckInMapper.selectByUserAndDateForUpdate(1, bizDate)).thenReturn(null, current);
        when(growthCheckInMapper.selectByUserAndDate(1, bizDate)).thenReturn(current);
        when(growthCheckInMapper.selectLatestBeforeDateForUpdate(1, bizDate)).thenReturn(null);
        when(growthCheckInMapper.insert(1, bizDate, 1)).thenThrow(new DataIntegrityViolationException("duplicate check-in"));
        when(growthCheckInMapper.selectLatestByUserId(1)).thenReturn(current);
        when(growthCheckInMapper.countByUserId(1)).thenReturn(7);
        when(growthCheckInMapper.maxStreakByUserId(1)).thenReturn(5);

        CheckInService.CheckInResult result = service.checkIn(1, bizDate);

        assertThat(result.newlyCheckedIn()).isFalse();
        assertThat(result.checkedInToday()).isTrue();
        assertThat(result.currentStreak()).isEqualTo(3);
        verify(walletRewardService, never()).issue(anyString(), anyInt(), org.mockito.ArgumentMatchers.anyLong(), anyString());
        verify(growthEventPublisher, never()).publishCheckInCompleted(anyString(), org.mockito.ArgumentMatchers.any());
    }
}
