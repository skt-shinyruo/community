package com.nowcoder.community.growth.service;

import com.nowcoder.community.growth.entity.RewardItem;
import com.nowcoder.community.growth.entity.RewardOrder;
import com.nowcoder.community.growth.mapper.RewardItemMapper;
import com.nowcoder.community.growth.mapper.RewardOrderMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RewardRedemptionServiceUnitTest {

    @Mock
    private RewardItemMapper rewardItemMapper;

    @Mock
    private RewardOrderMapper rewardOrderMapper;

    @Mock
    private RewardAccountService rewardAccountService;

    @Test
    void duplicateRedeemInsertRaceShouldReturnExistingOrderWithoutSpendingStockOrBalance() {
        RewardRedemptionService service = new RewardRedemptionService(rewardItemMapper, rewardOrderMapper, rewardAccountService);
        RewardItem item = new RewardItem();
        item.setId(12L);
        item.setItemName("头像框周卡");
        item.setItemDesc("自动发放");
        item.setCostBalance(8);
        item.setStock(5);
        item.setPerUserLimit(1);
        item.setFulfillmentMode("AUTO");
        item.setStatus("ACTIVE");

        RewardOrder existing = new RewardOrder();
        existing.setId(102L);
        existing.setRedeemRequestId("redeem-race-1");
        existing.setItemId(12L);
        existing.setStatus("FULFILLED");

        when(rewardOrderMapper.selectByUserIdAndRedeemRequestId(1, "redeem-race-1")).thenReturn(null);
        when(rewardOrderMapper.selectByUserIdAndRedeemRequestIdForUpdate(1, "redeem-race-1")).thenReturn(null, existing);
        when(rewardItemMapper.selectByIdForUpdate(12L)).thenReturn(item);
        when(rewardOrderMapper.insert(any(RewardOrder.class))).thenThrow(new DataIntegrityViolationException("duplicate request"));

        RewardOrder result = service.redeem(1, 12L, "redeem-race-1");

        assertThat(result.getId()).isEqualTo(102L);
        verify(rewardItemMapper, never()).decrementStockIfAvailable(anyLong());
        verifyNoInteractions(rewardAccountService);
    }

    @Test
    void duplicateRedeemDetectedAfterItemLockShouldReturnExistingOrderWithoutInsertOrReserve() {
        RewardRedemptionService service = new RewardRedemptionService(rewardItemMapper, rewardOrderMapper, rewardAccountService);
        RewardItem item = new RewardItem();
        item.setId(12L);
        item.setItemName("头像框周卡");
        item.setItemDesc("自动发放");
        item.setCostBalance(8);
        item.setStock(5);
        item.setPerUserLimit(1);
        item.setFulfillmentMode("AUTO");
        item.setStatus("ACTIVE");

        RewardOrder existing = new RewardOrder();
        existing.setId(103L);
        existing.setRedeemRequestId("redeem-lock-1");
        existing.setItemId(12L);
        existing.setStatus("FULFILLED");

        when(rewardOrderMapper.selectByUserIdAndRedeemRequestId(1, "redeem-lock-1")).thenReturn(null);
        when(rewardItemMapper.selectByIdForUpdate(12L)).thenReturn(item);
        when(rewardOrderMapper.selectByUserIdAndRedeemRequestIdForUpdate(1, "redeem-lock-1")).thenReturn(existing);

        RewardOrder result = service.redeem(1, 12L, "redeem-lock-1");

        assertThat(result.getId()).isEqualTo(103L);
        verify(rewardOrderMapper, never()).insert(any(RewardOrder.class));
        verify(rewardItemMapper, never()).reserveStockForRedemption(anyLong(), anyInt());
        verifyNoInteractions(rewardAccountService);
    }
}
