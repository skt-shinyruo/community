package com.nowcoder.community.market.domain.model;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.market.exception.MarketErrorCode;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MarketOrderTest {

    @Test
    void placeShouldCreateEscrowPendingOrderWithAddressSnapshot() {
        MarketOrder order = MarketOrder.place(physicalPlacement());

        assertThat(order.getOrderId()).isEqualTo(uuid(1));
        assertThat(order.getRequestId()).isEqualTo("market:req-physical");
        assertThat(order.getListingId()).isEqualTo(uuid(2));
        assertThat(order.getSellerUserId()).isEqualTo(uuid(3));
        assertThat(order.getBuyerUserId()).isEqualTo(uuid(4));
        assertThat(order.goodsType()).isEqualTo(MarketGoodsType.PHYSICAL);
        assertThat(order.status()).isEqualTo(MarketOrderStatus.ESCROW_PENDING);
        assertThat(order.getStatus()).isEqualTo("ESCROW_PENDING");
        assertThat(order.getAddressIdSnapshot()).isEqualTo(uuid(5));
        assertThat(order.getReceiverNameSnapshot()).isEqualTo("Buyer");
    }

    @Test
    void assertReplayMatchesShouldAllowSamePayload() {
        MarketOrder order = MarketOrder.place(physicalPlacement());

        order.assertReplayMatches(uuid(4), uuid(2), 1, uuid(5), null);
    }

    @Test
    void assertReplayMatchesShouldRejectDifferentQuantity() {
        MarketOrder order = MarketOrder.place(physicalPlacement());

        assertThatThrownBy(() -> order.assertReplayMatches(uuid(4), uuid(2), 2, uuid(5), null))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(MarketErrorCode.REQUEST_REPLAY_CONFLICT));
    }

    @Test
    void assertBuyerAndSellerShouldRejectWrongActor() {
        MarketOrder order = MarketOrder.place(physicalPlacement());

        assertThatThrownBy(() -> order.assertBuyer(uuid(6)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("actor is not market order buyer");
        assertThatThrownBy(() -> order.assertSeller(uuid(6)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("actor is not market order seller");
    }

    @Test
    void requestReleaseShouldRequireDeliveredOrShipped() {
        MarketOrder order = MarketOrder.place(physicalPlacement());

        assertThatThrownBy(order::requestRelease)
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("order is not confirmable");

        order.setStatus("SHIPPED");

        MarketOrderTransition transition = order.requestRelease();

        assertThat(transition.orderId()).isEqualTo(uuid(1));
        assertThat(transition.expectedStatuses())
                .containsExactlyInAnyOrder(MarketOrderStatus.DELIVERED, MarketOrderStatus.SHIPPED);
        assertThat(transition.nextStatus()).isEqualTo(MarketOrderStatus.RELEASE_PENDING);
    }

    @Test
    void cancelTransitionsShouldDependOnCurrentStatus() {
        MarketOrder order = MarketOrder.place(physicalPlacement());

        assertThat(order.requestEscrowCancel().nextStatus()).isEqualTo(MarketOrderStatus.ESCROW_CANCEL_PENDING);

        order.setStatus("ESCROWED");

        assertThat(order.requestRefund().nextStatus()).isEqualTo(MarketOrderStatus.REFUND_PENDING);
    }

    @Test
    void pendingWalletActionTypeShouldDelegateToStatus() {
        MarketOrder order = MarketOrder.place(physicalPlacement());
        assertThat(order.pendingWalletActionType()).isEqualTo(MarketWalletActionType.ESCROW);

        order.setStatus("DISPUTE_RELEASE_PENDING");

        assertThat(order.pendingWalletActionType()).isEqualTo(MarketWalletActionType.RELEASE);
    }

    @Test
    void autoConfirmShouldRequireDueConfirmableOrder() {
        MarketOrder order = MarketOrder.place(physicalPlacement());
        Date now = new Date(1_000L);
        order.setStatus("DELIVERED");
        order.setAutoConfirmAt(new Date(500L));

        assertThat(order.isAutoConfirmDue(now)).isTrue();

        order.setAutoConfirmAt(new Date(1_500L));

        assertThat(order.isAutoConfirmDue(now)).isFalse();
    }

    private static MarketOrderPlacement physicalPlacement() {
        return new MarketOrderPlacement(
                uuid(1),
                "market:req-physical",
                uuid(2),
                MarketGoodsType.PHYSICAL,
                uuid(3),
                uuid(4),
                1,
                12_900L,
                12_900L,
                MarketDeliveryMode.MANUAL,
                "Used keyboard",
                new MarketAddressSnapshot(
                        uuid(5),
                        "Buyer",
                        "13800000000",
                        "Zhejiang",
                        "Hangzhou",
                        "Xihu",
                        "Wensan Road 1",
                        "310000"
                )
        );
    }

    private static UUID uuid(int value) {
        return UUID.fromString(String.format("00000000-0000-7000-8000-%012d", value));
    }
}
