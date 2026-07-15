package com.nowcoder.community.market.domain.model;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.market.exception.MarketErrorCode;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.Set;
import java.util.UUID;

import static com.nowcoder.community.market.support.MarketOrderTestFixture.order;
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

        order.assertReplayMatches(uuid(4), uuid(2), 1, uuid(5));
    }

    @Test
    void assertReplayMatchesShouldRejectDifferentQuantity() {
        MarketOrder order = MarketOrder.place(physicalPlacement());

        assertThatThrownBy(() -> order.assertReplayMatches(uuid(4), uuid(2), 2, uuid(5)))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(MarketErrorCode.REQUEST_REPLAY_CONFLICT));
    }

    @Test
    void physicalReplayWithMissingPersistedAddressIdShouldConflict() {
        MarketOrder order = order(MarketOrder.place(physicalPlacement()))
                .addressIdSnapshot(null)
                .build();

        assertThatThrownBy(() -> order.assertReplayMatches(uuid(4), uuid(2), 1, uuid(5)))
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

        order = order(order).status("SHIPPED").build();

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

        order = order(order).status("ESCROWED").build();

        assertThat(order.requestRefund().nextStatus()).isEqualTo(MarketOrderStatus.REFUND_PENDING);
    }

    @Test
    void pendingWalletActionTypeShouldDelegateToStatus() {
        MarketOrder order = MarketOrder.place(physicalPlacement());
        assertThat(order.pendingWalletActionType()).isEqualTo(MarketWalletActionType.ESCROW);

        order = order(order).status("DISPUTE_RELEASE_PENDING").build();

        assertThat(order.pendingWalletActionType()).isEqualTo(MarketWalletActionType.RELEASE);
    }

    @Test
    void autoConfirmShouldRequireDueConfirmableOrder() {
        MarketOrder order = order(MarketOrder.place(physicalPlacement()))
                .status("DELIVERED")
                .autoConfirmAt(new Date(500L))
                .build();
        Date now = new Date(1_000L);

        assertThat(order.isAutoConfirmDue(now)).isTrue();

        order = order(order).autoConfirmAt(new Date(1_500L)).build();

        assertThat(order.isAutoConfirmDue(now)).isFalse();
    }

    @Test
    void autoConfirmShouldReturnFalseWhenNowIsNull() {
        MarketOrder order = order(MarketOrder.place(physicalPlacement()))
                .status("DELIVERED")
                .autoConfirmAt(new Date(500L))
                .build();

        assertThat(order.isAutoConfirmDue(null)).isFalse();
    }

    @Test
    void placementShouldRejectInvalidRequestIdQuantityAndAmounts() {
        assertThatThrownBy(() -> placement(" ", 1, 12_900L, 12_900L, MarketGoodsType.PHYSICAL, addressSnapshot(uuid(5))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("requestId must not be blank");
        assertThatThrownBy(() -> placement("market:req-physical", 0, 12_900L, 12_900L,
                MarketGoodsType.PHYSICAL, addressSnapshot(uuid(5))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("quantity must be positive");
        assertThatThrownBy(() -> placement("market:req-physical", 1, -1L, 12_900L,
                MarketGoodsType.PHYSICAL, addressSnapshot(uuid(5))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unitPriceSnapshot must not be negative");
        assertThatThrownBy(() -> placement("market:req-physical", 1, 12_900L, -1L,
                MarketGoodsType.PHYSICAL, addressSnapshot(uuid(5))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("totalAmount must not be negative");
    }

    @Test
    void physicalPlacementShouldRejectMissingAddressSnapshot() {
        assertThatThrownBy(() -> placement("market:req-physical", 1, 12_900L, 12_900L, MarketGoodsType.PHYSICAL, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("addressSnapshot must not be null for physical goods");
    }

    @Test
    void physicalPlacementShouldAllowNullDeliveryModeSnapshot() {
        MarketOrder order = MarketOrder.place(placement(
                "market:req-physical",
                1,
                12_900L,
                12_900L,
                MarketGoodsType.PHYSICAL,
                null,
                addressSnapshot(uuid(5))
        ));

        assertThat(order.getDeliveryModeSnapshot()).isNull();
    }

    @Test
    void virtualPlacementShouldRejectNullDeliveryModeSnapshot() {
        assertThatThrownBy(() -> placement(
                "market:req-virtual",
                1,
                12_900L,
                12_900L,
                MarketGoodsType.VIRTUAL,
                null,
                null
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("deliveryModeSnapshot");
    }

    @Test
    void virtualPlacementShouldRejectAddressSnapshot() {
        assertThatThrownBy(() -> placement("market:req-virtual", 1, 12_900L, 12_900L,
                MarketGoodsType.VIRTUAL, addressSnapshot(uuid(5))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("addressSnapshot must be null for virtual goods");
    }

    @Test
    void transitionShouldRejectMissingExpectedStatuses() {
        assertThatThrownBy(() -> new MarketOrderTransition(
                uuid(1),
                null,
                MarketOrderStatus.DELIVERED,
                null,
                null,
                null,
                null
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("expectedStatuses must not be null");
        assertThatThrownBy(() -> new MarketOrderTransition(
                uuid(1),
                Set.of(),
                MarketOrderStatus.DELIVERED,
                null,
                null,
                null,
                null
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expectedStatuses must not be empty");
    }

    @Test
    void transitionShouldDefensivelyCopyAutoConfirmAt() {
        Date autoConfirmAt = new Date(1_000L);
        MarketOrderTransition transition = MarketOrderTransition.delivered(uuid(1), autoConfirmAt);

        autoConfirmAt.setTime(2_000L);

        assertThat(transition.autoConfirmAt()).hasTime(1_000L);

        Date returnedAutoConfirmAt = transition.autoConfirmAt();
        returnedAutoConfirmAt.setTime(3_000L);

        assertThat(transition.autoConfirmAt()).hasTime(1_000L);
    }

    private static MarketOrderPlacement physicalPlacement() {
        return placement(
                "market:req-physical",
                1,
                12_900L,
                12_900L,
                MarketGoodsType.PHYSICAL,
                addressSnapshot(uuid(5))
        );
    }

    private static MarketOrderPlacement placement(
            String requestId,
            int quantity,
            long unitPriceSnapshot,
            long totalAmount,
            MarketGoodsType goodsType,
            MarketAddressSnapshot addressSnapshot
    ) {
        return placement(
                requestId,
                quantity,
                unitPriceSnapshot,
                totalAmount,
                goodsType,
                MarketDeliveryMode.MANUAL,
                addressSnapshot
        );
    }

    private static MarketOrderPlacement placement(
            String requestId,
            int quantity,
            long unitPriceSnapshot,
            long totalAmount,
            MarketGoodsType goodsType,
            MarketDeliveryMode deliveryModeSnapshot,
            MarketAddressSnapshot addressSnapshot
    ) {
        return new MarketOrderPlacement(
                uuid(1),
                requestId,
                uuid(2),
                goodsType,
                uuid(3),
                uuid(4),
                quantity,
                unitPriceSnapshot,
                totalAmount,
                deliveryModeSnapshot,
                "Used keyboard",
                addressSnapshot
        );
    }

    private static MarketAddressSnapshot addressSnapshot(UUID addressId) {
        return new MarketAddressSnapshot(
                addressId,
                "Buyer",
                "13800000000",
                "Zhejiang",
                "Hangzhou",
                "Xihu",
                "Wensan Road 1",
                "310000"
        );
    }

    private static UUID uuid(int value) {
        return UUID.fromString(String.format("00000000-0000-7000-8000-%012d", value));
    }
}
