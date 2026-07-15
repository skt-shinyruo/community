package com.nowcoder.community.market.application;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.exception.ErrorKind;
import com.nowcoder.community.common.id.UuidV7Generator;
import com.nowcoder.community.market.domain.model.MarketDeliveryMode;
import com.nowcoder.community.market.domain.model.MarketDispute;
import com.nowcoder.community.market.domain.model.MarketGoodsType;
import com.nowcoder.community.market.domain.model.MarketOrder;
import com.nowcoder.community.market.domain.model.MarketOrderStatus;
import com.nowcoder.community.market.domain.repository.MarketAddressRepository;
import com.nowcoder.community.market.domain.repository.MarketDeliveryRepository;
import com.nowcoder.community.market.domain.repository.MarketDisputeRepository;
import com.nowcoder.community.market.domain.repository.MarketInventoryRepository;
import com.nowcoder.community.market.domain.repository.MarketListingRepository;
import com.nowcoder.community.market.domain.repository.MarketOrderRepository;
import com.nowcoder.community.market.domain.repository.MarketShipmentRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.invocation.InvocationOnMock;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Date;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.when;

class MarketOrderTransitionApplicationServiceTest {

    private static final UUID ORDER_ID = uuid(501);
    private static final UUID DISPUTE_ID = uuid(502);
    private static final UUID SELLER_ID = uuid(503);
    private static final UUID BUYER_ID = uuid(504);
    private static final long TOTAL_AMOUNT = 1_200L;

    @Test
    void userCommandShouldMapStaleTransitionToConflictWithoutEnqueuingWalletAction() {
        MarketOrderRepository orderRepository = staleOrderRepository();
        MarketWalletActionApplicationService walletActionService = mock(MarketWalletActionApplicationService.class);
        MarketOrder delivered = orderIn(MarketOrderStatus.DELIVERED, MarketGoodsType.PHYSICAL, new Date(1_000L));
        when(orderRepository.lockById(ORDER_ID)).thenReturn(delivered);
        when(orderRepository.findById(ORDER_ID)).thenReturn(delivered);
        MarketOrderApplicationService service = orderService(orderRepository, walletActionService);

        Throwable failure = catchThrowable(() -> service.confirmOrder(ORDER_ID, BUYER_ID));

        assertStaleConflictAndGenericApply(failure, orderRepository, "markReleasePending");
        assertSoftly(softly -> softly.assertThat(invocationNames(walletActionService))
                .as("stale confirmation must not enqueue release")
                .isEmpty());
    }

    @Test
    void deliveryCommandShouldFailClosedWhenTransitionIsStale() {
        MarketOrderRepository orderRepository = staleOrderRepository();
        MarketWalletActionApplicationService walletActionService = mock(MarketWalletActionApplicationService.class);
        MarketOrder escrowed = orderIn(MarketOrderStatus.ESCROWED, MarketGoodsType.VIRTUAL, null);
        when(orderRepository.lockById(ORDER_ID)).thenReturn(escrowed);
        when(orderRepository.findById(ORDER_ID)).thenReturn(escrowed);
        MarketOrderApplicationService service = orderService(orderRepository, walletActionService);

        Throwable failure = catchThrowable(() -> service.deliverVirtualOrder(ORDER_ID, SELLER_ID, "CODE-001"));

        assertStaleConflictAndGenericApply(failure, orderRepository, "markDelivered");
    }

    @Test
    void disputeResolutionShouldNotPersistAnExternalActionAfterStaleOrderTransition() {
        MarketOrderRepository orderRepository = staleOrderRepository();
        MarketDisputeRepository disputeRepository = mock(MarketDisputeRepository.class);
        MarketWalletActionApplicationService walletActionService = mock(MarketWalletActionApplicationService.class);
        MarketDispute openDispute = openDispute();
        when(disputeRepository.lockById(DISPUTE_ID)).thenReturn(openDispute);
        when(disputeRepository.findById(DISPUTE_ID)).thenReturn(openDispute);
        when(orderRepository.lockById(ORDER_ID))
                .thenReturn(orderIn(MarketOrderStatus.DISPUTED, MarketGoodsType.PHYSICAL, null));
        MarketDisputeApplicationService service = new MarketDisputeApplicationService(
                disputeRepository,
                orderRepository,
                walletActionService,
                new UuidV7Generator()
        );

        Throwable failure = catchThrowable(() -> service.adminResolveRelease(
                DISPUTE_ID,
                uuid(599),
                "release after review"
        ));

        assertStaleConflictAndGenericApply(failure, orderRepository, "markDisputeReleasePending");
        assertSoftly(softly -> softly.assertThat(invocationNames(walletActionService))
                .as("stale dispute resolution must not enqueue release")
                .isEmpty());
    }

    @Test
    void autoConfirmShouldMapStaleTransitionToFalseWithoutEnqueuingRelease() {
        MarketOrderRepository orderRepository = staleOrderRepository();
        MarketWalletActionApplicationService walletActionService = mock(MarketWalletActionApplicationService.class);
        Date now = new Date(2_000L);
        when(orderRepository.lockById(ORDER_ID)).thenReturn(
                orderIn(MarketOrderStatus.DELIVERED, MarketGoodsType.PHYSICAL, new Date(1_000L))
        );
        MarketOrderAutoConfirmSingleOrderApplicationService service =
                new MarketOrderAutoConfirmSingleOrderApplicationService(orderRepository, walletActionService);

        boolean confirmed = service.confirmOneDueOrder(ORDER_ID, now);

        assertSoftly(softly -> {
            softly.assertThat(confirmed).isFalse();
            softly.assertThat(invocationNames(orderRepository))
                    .contains("apply")
                    .doesNotContain("markReleasePending");
            softly.assertThat(invocationNames(walletActionService)).isEmpty();
        });
    }

    @Test
    void sagaShouldLockAggregateAndMapStaleTransitionToFalse() {
        MarketOrderRepository orderRepository = staleOrderRepository();
        when(orderRepository.lockById(ORDER_ID)).thenReturn(
                orderIn(MarketOrderStatus.ESCROW_PENDING, MarketGoodsType.PHYSICAL, null)
        );
        MarketOrderSagaApplicationService service = new MarketOrderSagaApplicationService(
                orderRepository,
                mock(MarketListingRepository.class),
                mock(MarketInventoryRepository.class)
        );

        boolean advanced = service.markEscrowSucceeded(ORDER_ID, uuid(601));

        assertSoftly(softly -> {
            softly.assertThat(advanced).isFalse();
            softly.assertThat(invocationNames(orderRepository))
                    .contains("lockById", "apply")
                    .doesNotContain("markEscrowSucceeded");
        });
    }

    private static MarketOrderApplicationService orderService(
            MarketOrderRepository orderRepository,
            MarketWalletActionApplicationService walletActionService
    ) {
        return new MarketOrderApplicationService(
                mock(MarketListingRepository.class),
                mock(MarketInventoryRepository.class),
                orderRepository,
                mock(MarketAddressRepository.class),
                mock(MarketDeliveryRepository.class),
                mock(MarketShipmentRepository.class),
                walletActionService,
                mock(MarketOrderSagaApplicationService.class),
                new UuidV7Generator()
        );
    }

    private static MarketOrderRepository staleOrderRepository() {
        return mock(MarketOrderRepository.class, MarketOrderTransitionApplicationServiceTest::staleByDefault);
    }

    private static Object staleByDefault(InvocationOnMock invocation) throws Throwable {
        if (!invocation.getMethod().getName().equals("apply")) {
            return Answers.RETURNS_DEFAULTS.answer(invocation);
        }
        Object[] constants = invocation.getMethod().getReturnType().getEnumConstants();
        if (constants == null) {
            throw new AssertionError("MarketOrderRepository.apply must return a semantic enum");
        }
        return Arrays.stream(constants)
                .filter(constant -> constant.toString().equals("STALE"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("MarketOrderRepository.apply result must contain STALE"));
    }

    private static void assertStaleConflictAndGenericApply(
            Throwable failure,
            MarketOrderRepository orderRepository,
            String legacyMethod
    ) {
        assertSoftly(softly -> {
            softly.assertThat(failure)
                    .as("stale foreground transition must fail with a business conflict")
                    .isInstanceOfSatisfying(BusinessException.class, error ->
                            softly.assertThat(error.getErrorCode().getKind()).isEqualTo(ErrorKind.CONFLICT));
            softly.assertThat(invocationNames(orderRepository))
                    .contains("apply")
                    .doesNotContain(legacyMethod);
        });
    }

    private static Set<String> invocationNames(Object mock) {
        return mockingDetails(mock).getInvocations().stream()
                .map(invocation -> invocation.getMethod().getName())
                .collect(Collectors.toSet());
    }

    private static MarketDispute openDispute() {
        MarketDispute dispute = new MarketDispute();
        dispute.setDisputeId(DISPUTE_ID);
        dispute.setOrderId(ORDER_ID);
        dispute.setGoodsType(MarketGoodsType.PHYSICAL.code());
        dispute.setBuyerUserId(BUYER_ID);
        dispute.setSellerUserId(SELLER_ID);
        dispute.setStatus("OPEN");
        dispute.setReason("item mismatch");
        dispute.setBuyerNote("not as described");
        return dispute;
    }

    private static MarketOrder orderIn(
            MarketOrderStatus status,
            MarketGoodsType goodsType,
            Date autoConfirmAt
    ) {
        MarketOrder order = instantiateOrder();
        setField(order, "orderId", ORDER_ID);
        setField(order, "requestId", "market:application-transition");
        setField(order, "listingId", uuid(505));
        setField(order, "goodsType", goodsType.code());
        setField(order, "sellerUserId", SELLER_ID);
        setField(order, "buyerUserId", BUYER_ID);
        setField(order, "quantity", 1);
        setField(order, "unitPriceSnapshot", TOTAL_AMOUNT);
        setField(order, "totalAmount", TOTAL_AMOUNT);
        setField(order, "deliveryModeSnapshot", MarketDeliveryMode.MANUAL.code());
        setField(order, "listingTitleSnapshot", "Market item");
        setField(order, "status", status.code());
        setField(order, "autoConfirmAt", autoConfirmAt);
        return order;
    }

    private static MarketOrder instantiateOrder() {
        try {
            Constructor<MarketOrder> constructor = MarketOrder.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException error) {
            throw new AssertionError("MarketOrder must retain a non-public persistence constructor", error);
        }
    }

    private static void setField(MarketOrder order, String name, Object value) {
        try {
            Field field = MarketOrder.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(order, value);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError("Cannot prepare MarketOrder field: " + name, error);
        }
    }
}
