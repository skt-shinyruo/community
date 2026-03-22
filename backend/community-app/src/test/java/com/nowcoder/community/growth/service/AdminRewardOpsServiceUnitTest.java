package com.nowcoder.community.growth.service;

import com.nowcoder.community.growth.dto.AdminRewardOrderActionRequest;
import com.nowcoder.community.growth.entity.RewardOrder;
import com.nowcoder.community.growth.mapper.AdminRewardOrderActionMapper;
import com.nowcoder.community.growth.mapper.RewardItemMapper;
import com.nowcoder.community.growth.mapper.RewardOrderMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminRewardOpsServiceUnitTest {

    @Mock
    private RewardItemMapper rewardItemMapper;

    @Mock
    private RewardOrderMapper rewardOrderMapper;

    @Mock
    private AdminRewardOrderActionMapper adminRewardOrderActionMapper;

    @Mock
    private RewardRedemptionService rewardRedemptionService;

    @Test
    void processOrderShouldNotAuditWhenLockedCurrentStateIsAlreadyFulfilled() {
        AdminRewardOpsService service = new AdminRewardOpsService(
                rewardItemMapper,
                rewardOrderMapper,
                adminRewardOrderActionMapper,
                rewardRedemptionService
        );
        AdminRewardOrderActionRequest request = new AdminRewardOrderActionRequest();
        request.setOrderId(12L);
        request.setAction("FULFILL");
        request.setConfirm(true);
        request.setNote("issued");

        RewardOrder fulfilled = new RewardOrder();
        fulfilled.setId(12L);
        fulfilled.setStatus("FULFILLED");

        when(rewardOrderMapper.selectByIdForUpdate(12L)).thenReturn(fulfilled);
        when(rewardRedemptionService.fulfillPendingOrder(12L)).thenReturn(fulfilled);

        RewardOrder result = service.processOrder(99, request);

        assertThat(result.getStatus()).isEqualTo("FULFILLED");
        verify(adminRewardOrderActionMapper, never()).insert(any());
    }
}
