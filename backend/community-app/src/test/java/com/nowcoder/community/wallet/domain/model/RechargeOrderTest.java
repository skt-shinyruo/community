package com.nowcoder.community.wallet.domain.model;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.wallet.exception.WalletErrorCode;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RechargeOrderTest {

    @Test
    void createShouldBuildCreatedOrder() {
        UUID orderId = uuid(1);
        UUID userId = uuid(2);

        RechargeOrder order = RechargeOrder.create(orderId, "recharge:req-domain", userId, 1200L);

        assertThat(order.getOrderId()).isEqualTo(orderId);
        assertThat(order.getRequestId()).isEqualTo("recharge:req-domain");
        assertThat(order.getUserId()).isEqualTo(userId);
        assertThat(order.getAmount()).isEqualTo(1200L);
        assertThat(order.status()).isEqualTo(RechargeOrderStatus.CREATED);
        assertThat(order.getStatus()).isEqualTo("CREATED");
        assertThat(order.isPaid()).isFalse();
    }

    @Test
    void assertReplayMatchesShouldAllowSameUserAndAmount() {
        RechargeOrder order = RechargeOrder.create(uuid(1), "recharge:req-replay", uuid(2), 1200L);

        order.assertReplayMatches(uuid(2), 1200L);
    }

    @Test
    void assertReplayMatchesShouldRejectDifferentPayload() {
        RechargeOrder order = RechargeOrder.create(uuid(1), "recharge:req-replay", uuid(2), 1200L);

        assertThatThrownBy(() -> order.assertReplayMatches(uuid(2), 1300L))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(WalletErrorCode.REQUEST_REPLAY_CONFLICT));
    }

    @Test
    void payShouldReturnCreatedToPaidTransition() {
        RechargeOrder order = RechargeOrder.create(uuid(1), "recharge:req-pay", uuid(2), 1200L);

        RechargeOrderTransition transition = order.pay();

        assertThat(transition.orderId()).isEqualTo(uuid(1));
        assertThat(transition.userId()).isEqualTo(uuid(2));
        assertThat(transition.requestId()).isEqualTo("recharge:req-pay");
        assertThat(transition.fromStatus()).isEqualTo(RechargeOrderStatus.CREATED);
        assertThat(transition.toStatus()).isEqualTo(RechargeOrderStatus.PAID);
    }

    @Test
    void payShouldRejectAlreadyPaidOrder() {
        RechargeOrder order = RechargeOrder.create(uuid(1), "recharge:req-paid", uuid(2), 1200L);
        order.setStatus("PAID");

        assertThat(order.isPaid()).isTrue();
        assertThatThrownBy(order::pay)
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("recharge order status mismatch");
    }

    private static UUID uuid(int value) {
        return UUID.fromString(String.format("00000000-0000-7000-8000-%012d", value));
    }
}
