package com.nowcoder.community.market.domain.model;

import com.nowcoder.community.common.exception.BusinessException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Date;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MarketOrderStateMachineTest {

    private static final UUID ORDER_ID = uuid(1);
    private static final UUID ESCROW_TXN_ID = uuid(101);
    private static final UUID RELEASE_TXN_ID = uuid(102);
    private static final UUID REFUND_TXN_ID = uuid(103);
    private static final Date AUTO_CONFIRM_AT = new Date(2_000L);

    @ParameterizedTest(name = "{0}: {1} -> {2}")
    @MethodSource("legalTransitions")
    void shouldProduceCompleteTransitionForEveryLegalEdge(
            TransitionCase transitionCase,
            MarketOrderStatus source,
            MarketOrderStatus target
    ) {
        MarketOrderTransition transition = transitionCase.invoke(orderIn(source));

        assertThat(transition.orderId()).isEqualTo(ORDER_ID);
        assertThat(transition.expectedStatuses()).containsExactlyInAnyOrderElementsOf(
                transitionCase.expectedStatuses(source)
        );
        assertThat(transition.nextStatus()).isEqualTo(target);
        assertThat(transition.escrowTxnId()).isEqualTo(transitionCase.escrowTxnId());
        assertThat(transition.releaseTxnId()).isEqualTo(transitionCase.releaseTxnId());
        assertThat(transition.refundTxnId()).isEqualTo(transitionCase.refundTxnId());
        assertThat(transition.autoConfirmAt()).isEqualTo(transitionCase.autoConfirmAt());
        assertThat(invokeAccessor(transition, "autoConfirmPolicy"))
                .hasToString(transitionCase.autoConfirmPolicy());
    }

    @ParameterizedTest(name = "{0} must reject source state {1}")
    @MethodSource("illegalTransitions")
    void shouldRejectEveryIllegalSourceState(TransitionCase transitionCase, MarketOrderStatus source) {
        assertThatThrownBy(() -> transitionCase.invoke(orderIn(source)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("order");
    }

    @ParameterizedTest(name = "{0} must require its completion value")
    @MethodSource("requiredCompletionValues")
    void shouldRejectMissingTransactionIdOrAutoConfirmTime(
            String methodName,
            Class<?> parameterType,
            MarketOrderStatus source
    ) {
        MarketOrder order = orderIn(source);

        assertThatThrownBy(() -> invoke(order, methodName, new Class<?>[]{parameterType}, new Object[]{null}))
                .isInstanceOfAny(NullPointerException.class, IllegalArgumentException.class);
    }

    private static Stream<Arguments> legalTransitions() {
        return transitionCases().flatMap(transitionCase -> transitionCase.legalEdges().entrySet().stream()
                .map(edge -> Arguments.of(transitionCase, edge.getKey(), edge.getValue())));
    }

    private static Stream<Arguments> illegalTransitions() {
        return transitionCases().flatMap(transitionCase -> Arrays.stream(MarketOrderStatus.values())
                .filter(status -> !transitionCase.legalEdges().containsKey(status))
                .map(status -> Arguments.of(transitionCase, status)));
    }

    private static Stream<Arguments> requiredCompletionValues() {
        return Stream.of(
                Arguments.of("recordEscrowSucceeded", UUID.class, MarketOrderStatus.ESCROW_PENDING),
                Arguments.of("recordLateEscrowSucceeded", UUID.class, MarketOrderStatus.ESCROW_CANCEL_PENDING),
                Arguments.of("recordReleaseSucceeded", UUID.class, MarketOrderStatus.RELEASE_PENDING),
                Arguments.of("recordRefundSucceeded", UUID.class, MarketOrderStatus.REFUND_PENDING),
                Arguments.of("markDelivered", Date.class, MarketOrderStatus.ESCROWED),
                Arguments.of("markShipped", Date.class, MarketOrderStatus.ESCROWED)
        );
    }

    private static Stream<TransitionCase> transitionCases() {
        return Stream.of(
                transition(
                        "escrow succeeds",
                        Map.of(MarketOrderStatus.ESCROW_PENDING, MarketOrderStatus.ESCROWED),
                        "recordEscrowSucceeded",
                        UUID.class,
                        ESCROW_TXN_ID,
                        ESCROW_TXN_ID,
                        null,
                        null,
                        "KEEP",
                        null,
                        false
                ),
                transition(
                        "escrow fails",
                        Map.of(MarketOrderStatus.ESCROW_PENDING, MarketOrderStatus.ESCROW_FAILED),
                        "recordEscrowFailed",
                        false
                ),
                transition(
                        "escrow cancellation is requested",
                        Map.of(MarketOrderStatus.ESCROW_PENDING, MarketOrderStatus.ESCROW_CANCEL_PENDING),
                        "requestEscrowCancel",
                        false
                ),
                transition(
                        "late escrow requires refund",
                        Map.of(MarketOrderStatus.ESCROW_CANCEL_PENDING, MarketOrderStatus.REFUND_PENDING),
                        "recordLateEscrowSucceeded",
                        UUID.class,
                        ESCROW_TXN_ID,
                        ESCROW_TXN_ID,
                        null,
                        null,
                        "KEEP",
                        null,
                        false
                ),
                transition(
                        "failed escrow is cancelled without refund",
                        Map.of(
                                MarketOrderStatus.ESCROW_CANCEL_PENDING, MarketOrderStatus.CANCELLED,
                                MarketOrderStatus.ESCROW_FAILED, MarketOrderStatus.CANCELLED
                        ),
                        "cancelWithoutRefund",
                        false
                ),
                transition(
                        "virtual order is delivered",
                        Map.of(MarketOrderStatus.ESCROWED, MarketOrderStatus.DELIVERED),
                        "markDelivered",
                        Date.class,
                        AUTO_CONFIRM_AT,
                        null,
                        null,
                        null,
                        "SET",
                        AUTO_CONFIRM_AT,
                        false
                ),
                transition(
                        "physical order is shipped",
                        Map.of(MarketOrderStatus.ESCROWED, MarketOrderStatus.SHIPPED),
                        "markShipped",
                        Date.class,
                        AUTO_CONFIRM_AT,
                        null,
                        null,
                        null,
                        "SET",
                        AUTO_CONFIRM_AT,
                        false
                ),
                transition(
                        "escrowed order requests refund",
                        Map.of(MarketOrderStatus.ESCROWED, MarketOrderStatus.REFUND_PENDING),
                        "requestRefund",
                        false
                ),
                transition(
                        "buyer or timer requests release",
                        Map.of(
                                MarketOrderStatus.DELIVERED, MarketOrderStatus.RELEASE_PENDING,
                                MarketOrderStatus.SHIPPED, MarketOrderStatus.RELEASE_PENDING
                        ),
                        "requestRelease",
                        false
                ),
                transition(
                        "buyer opens dispute",
                        Map.of(
                                MarketOrderStatus.DELIVERED, MarketOrderStatus.DISPUTED,
                                MarketOrderStatus.SHIPPED, MarketOrderStatus.DISPUTED
                        ),
                        "openDispute",
                        false
                ),
                transition(
                        "dispute requests refund",
                        Map.of(MarketOrderStatus.DISPUTED, MarketOrderStatus.DISPUTE_REFUND_PENDING),
                        "requestDisputeRefund",
                        false
                ),
                transition(
                        "dispute requests release",
                        Map.of(MarketOrderStatus.DISPUTED, MarketOrderStatus.DISPUTE_RELEASE_PENDING),
                        "requestDisputeRelease",
                        false
                ),
                transition(
                        "release succeeds",
                        Map.of(
                                MarketOrderStatus.RELEASE_PENDING, MarketOrderStatus.COMPLETED,
                                MarketOrderStatus.DISPUTE_RELEASE_PENDING, MarketOrderStatus.COMPLETED
                        ),
                        "recordReleaseSucceeded",
                        UUID.class,
                        RELEASE_TXN_ID,
                        null,
                        RELEASE_TXN_ID,
                        null,
                        "CLEAR",
                        null,
                        false
                ),
                transition(
                        "refund succeeds",
                        Map.of(
                                MarketOrderStatus.REFUND_PENDING, MarketOrderStatus.CANCELLED,
                                MarketOrderStatus.DISPUTE_REFUND_PENDING, MarketOrderStatus.REFUNDED
                        ),
                        "recordRefundSucceeded",
                        UUID.class,
                        REFUND_TXN_ID,
                        null,
                        null,
                        REFUND_TXN_ID,
                        "KEEP",
                        null,
                        true
                )
        );
    }

    private static TransitionCase transition(
            String action,
            Map<MarketOrderStatus, MarketOrderStatus> legalEdges,
            String methodName,
            boolean sourceSpecificExpectedStatus
    ) {
        return new TransitionCase(
                action,
                legalEdges,
                methodName,
                new Class<?>[0],
                new Object[0],
                null,
                null,
                null,
                "KEEP",
                null,
                sourceSpecificExpectedStatus
        );
    }

    private static TransitionCase transition(
            String action,
            Map<MarketOrderStatus, MarketOrderStatus> legalEdges,
            String methodName,
            Class<?> parameterType,
            Object argument,
            UUID escrowTxnId,
            UUID releaseTxnId,
            UUID refundTxnId,
            String autoConfirmPolicy,
            Date autoConfirmAt,
            boolean sourceSpecificExpectedStatus
    ) {
        return new TransitionCase(
                action,
                legalEdges,
                methodName,
                new Class<?>[]{parameterType},
                new Object[]{argument},
                escrowTxnId,
                releaseTxnId,
                refundTxnId,
                autoConfirmPolicy,
                autoConfirmAt,
                sourceSpecificExpectedStatus
        );
    }

    private static MarketOrder orderIn(MarketOrderStatus status) {
        MarketOrder order = instantiateOrder();
        setField(order, "orderId", ORDER_ID);
        setField(order, "requestId", "market:state-machine");
        setField(order, "listingId", uuid(2));
        setField(order, "goodsType", MarketGoodsType.PHYSICAL.code());
        setField(order, "sellerUserId", uuid(3));
        setField(order, "buyerUserId", uuid(4));
        setField(order, "quantity", 1);
        setField(order, "unitPriceSnapshot", 1_200L);
        setField(order, "totalAmount", 1_200L);
        setField(order, "deliveryModeSnapshot", MarketDeliveryMode.MANUAL.code());
        setField(order, "listingTitleSnapshot", "Used keyboard");
        setField(order, "status", status.code());
        setField(order, "autoConfirmAt", new Date(1_000L));
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

    private static Object invokeAccessor(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError("Missing transition accessor: " + methodName, error);
        }
    }

    private static MarketOrderTransition invoke(
            MarketOrder order,
            String methodName,
            Class<?>[] parameterTypes,
            Object[] arguments
    ) {
        try {
            Method method = MarketOrder.class.getMethod(methodName, parameterTypes);
            return (MarketOrderTransition) method.invoke(order, arguments);
        } catch (NoSuchMethodException error) {
            throw new AssertionError("Missing MarketOrder behavior: " + methodName, error);
        } catch (IllegalAccessException error) {
            throw new AssertionError("MarketOrder behavior is not accessible: " + methodName, error);
        } catch (InvocationTargetException error) {
            Throwable cause = error.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error invocationError) {
                throw invocationError;
            }
            throw new AssertionError(cause);
        }
    }

    private record TransitionCase(
            String action,
            Map<MarketOrderStatus, MarketOrderStatus> legalEdges,
            String methodName,
            Class<?>[] parameterTypes,
            Object[] arguments,
            UUID escrowTxnId,
            UUID releaseTxnId,
            UUID refundTxnId,
            String autoConfirmPolicy,
            Date autoConfirmAt,
            boolean sourceSpecificExpectedStatus
    ) {
        private MarketOrderTransition invoke(MarketOrder order) {
            return MarketOrderStateMachineTest.invoke(order, methodName, parameterTypes, arguments);
        }

        private Set<MarketOrderStatus> expectedStatuses(MarketOrderStatus source) {
            return sourceSpecificExpectedStatus ? Set.of(source) : legalEdges.keySet();
        }

        @Override
        public String toString() {
            return action;
        }
    }
}
