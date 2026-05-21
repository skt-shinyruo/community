package com.nowcoder.community.market.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MarketOrderStatusTest {

    @Test
    void fromCodeShouldResolvePersistedCodes() {
        assertThat(MarketOrderStatus.fromCode("ESCROW_PENDING")).isEqualTo(MarketOrderStatus.ESCROW_PENDING);
        assertThat(MarketOrderStatus.fromCode("ESCROWED")).isEqualTo(MarketOrderStatus.ESCROWED);
        assertThat(MarketOrderStatus.fromCode("DELIVERED")).isEqualTo(MarketOrderStatus.DELIVERED);
        assertThat(MarketOrderStatus.fromCode("SHIPPED")).isEqualTo(MarketOrderStatus.SHIPPED);
        assertThat(MarketOrderStatus.fromCode("RELEASE_PENDING")).isEqualTo(MarketOrderStatus.RELEASE_PENDING);
        assertThat(MarketOrderStatus.fromCode("COMPLETED")).isEqualTo(MarketOrderStatus.COMPLETED);
        assertThat(MarketOrderStatus.fromCode("REFUND_PENDING")).isEqualTo(MarketOrderStatus.REFUND_PENDING);
        assertThat(MarketOrderStatus.fromCode("CANCELLED")).isEqualTo(MarketOrderStatus.CANCELLED);
        assertThat(MarketOrderStatus.fromCode("ESCROW_CANCEL_PENDING")).isEqualTo(MarketOrderStatus.ESCROW_CANCEL_PENDING);
        assertThat(MarketOrderStatus.fromCode("ESCROW_FAILED")).isEqualTo(MarketOrderStatus.ESCROW_FAILED);
        assertThat(MarketOrderStatus.fromCode("DISPUTED")).isEqualTo(MarketOrderStatus.DISPUTED);
        assertThat(MarketOrderStatus.fromCode("DISPUTE_REFUND_PENDING"))
                .isEqualTo(MarketOrderStatus.DISPUTE_REFUND_PENDING);
        assertThat(MarketOrderStatus.fromCode("DISPUTE_RELEASE_PENDING"))
                .isEqualTo(MarketOrderStatus.DISPUTE_RELEASE_PENDING);
        assertThat(MarketOrderStatus.fromCode("REFUNDED")).isEqualTo(MarketOrderStatus.REFUNDED);
    }

    @Test
    void fromCodeShouldRejectUnknownCode() {
        assertThatThrownBy(() -> MarketOrderStatus.fromCode("BROKEN"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown market order status");
    }

    @Test
    void pendingWalletActionTypeShouldMapOnlyPendingStatuses() {
        assertThat(MarketOrderStatus.ESCROW_PENDING.pendingWalletActionType())
                .isEqualTo(MarketWalletActionType.ESCROW);
        assertThat(MarketOrderStatus.ESCROW_CANCEL_PENDING.pendingWalletActionType())
                .isEqualTo(MarketWalletActionType.ESCROW);
        assertThat(MarketOrderStatus.RELEASE_PENDING.pendingWalletActionType())
                .isEqualTo(MarketWalletActionType.RELEASE);
        assertThat(MarketOrderStatus.DISPUTE_RELEASE_PENDING.pendingWalletActionType())
                .isEqualTo(MarketWalletActionType.RELEASE);
        assertThat(MarketOrderStatus.REFUND_PENDING.pendingWalletActionType())
                .isEqualTo(MarketWalletActionType.REFUND);
        assertThat(MarketOrderStatus.DISPUTE_REFUND_PENDING.pendingWalletActionType())
                .isEqualTo(MarketWalletActionType.REFUND);
        assertThat(MarketOrderStatus.COMPLETED.pendingWalletActionType()).isNull();
    }

    @Test
    void lifecyclePredicatesShouldOnlyMatchDeliveredAndShipped() {
        assertThat(MarketOrderStatus.DELIVERED.isConfirmable()).isTrue();
        assertThat(MarketOrderStatus.DELIVERED.isDisputable()).isTrue();
        assertThat(MarketOrderStatus.SHIPPED.isConfirmable()).isTrue();
        assertThat(MarketOrderStatus.SHIPPED.isDisputable()).isTrue();

        assertThat(MarketOrderStatus.ESCROW_PENDING.isConfirmable()).isFalse();
        assertThat(MarketOrderStatus.ESCROW_PENDING.isDisputable()).isFalse();
    }
}
